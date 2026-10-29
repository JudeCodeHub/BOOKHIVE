"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CircleAlert, CircleCheck, CircleHelp, CircleX, Repeat } from "lucide-react";
import { useParams } from "next/navigation";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { BackLink } from "@/components/layout/back-link";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FormAlert } from "@/features/auth/auth-card";
import { useErrorMessage } from "@/features/auth/errors";
import { ScanInput } from "@/features/desk/scan-input";
import { playSound, type DeskSound } from "@/features/desk/sounds";
import { DataTable } from "@/features/reports/charts";
import { Pager } from "@/features/staff/pager";
import { api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";
import { Progress } from "./progress";
import { STOCKTAKE_KEY, Scope, StatusBadge, type Stocktake } from "./stocktake-list";

type ScanResult = Schema<"ScanResultResponse">;
type Missing = Schema<"MissingCopySummary">;
type Issue = Schema<"StocktakeIssueSummary">;

const MISSING_PAGE = 100;

/** What each answer sounds like (the desk's sounds, so staff already know them). */
function soundFor(r: ScanResult): DeskSound {
  if (r.alreadyScanned || r.result === "NOT_IN_SCOPE") return "warning";
  if (r.result === "FOUND") return "success";
  if (r.result === "NOT_ON_SHELF") return "hold";
  return "error";
}

/**
 * One stock-take (RPT-6, checklist step 375). While open: scan every copy on the shelves (scanner,
 * typing, or the camera) and see at once whether it belongs; the bar fills up as expected copies
 * are found. Then complete it (the numbers are kept) or cancel. Missing copies and scans that need
 * a look are listed at any time.
 */
export function StocktakeSession() {
  const t = useTranslations("Stocktake");
  const tCommon = useTranslations("Common");
  const errorText = useErrorMessage("Stocktake");
  const queryClient = useQueryClient();
  const { id } = useParams<{ id: string }>();
  const [last, setLast] = useState<ScanResult | null>(null);
  const [recent, setRecent] = useState<ScanResult[]>([]);
  const [finishing, setFinishing] = useState<"complete" | "cancel" | null>(null);
  const detailKey = [...STOCKTAKE_KEY, "detail", id];

  const session = useQuery({
    queryKey: detailKey,
    queryFn: () => api.get<Stocktake>(`/staff/stocktakes/${id}`),
  });

  const scan = useMutation({
    mutationFn: (barcode: string) =>
      api.post<ScanResult>(`/staff/stocktakes/${id}/scans`, { barcode }),
    onSuccess: (r) => {
      playSound(soundFor(r));
      setLast(r);
      setRecent((list) => [r, ...list].slice(0, 12));
      // The counts come with every answer: no need to fetch the session again
      queryClient.setQueryData<Stocktake>(detailKey, (s) => (s ? { ...s, counts: r.counts } : s));
      if (r.result !== "FOUND" || r.alreadyScanned) {
        void queryClient.invalidateQueries({ queryKey: [...STOCKTAKE_KEY, "issues", id] });
      }
      void queryClient.invalidateQueries({ queryKey: [...STOCKTAKE_KEY, "missing", id] });
    },
    onError: (error) => {
      playSound("error");
      toast.error(errorText(error));
    },
  });

  const finish = useMutation({
    mutationFn: (how: "complete" | "cancel") =>
      api.post<Stocktake>(`/staff/stocktakes/${id}/${how}`),
    onSuccess: (s, how) => {
      setFinishing(null);
      queryClient.setQueryData(detailKey, s);
      void queryClient.invalidateQueries({ queryKey: STOCKTAKE_KEY });
      toast.success(how === "complete" ? t("completed") : t("cancelled"));
    },
    onError: (error) => {
      setFinishing(null);
      toast.error(errorText(error));
    },
  });

  if (session.isPending) return <HexLoader label={tCommon("loading")} />;
  if (session.isError) return <FormAlert>{errorText(session.error)}</FormAlert>;

  const s = session.data;
  const open = s.status === "OPEN";

  return (
    <div className="grid gap-5">
      <BackLink href="/staff/stocktake">{t("back")}</BackLink>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-xl font-semibold">{s.name}</h1>
        <StatusBadge status={s.status} />
        {open && (
          <div className="ml-auto flex gap-2">
            <Button variant="ghost" onClick={() => setFinishing("cancel")}>
              {t("cancelStocktake")}
            </Button>
            <Button onClick={() => setFinishing("complete")}>{t("complete")}</Button>
          </div>
        )}
      </div>
      <p className="-mt-3 text-sm text-muted-foreground">
        <Scope stocktake={s} />
      </p>

      {s.status !== "CANCELLED" && (
        <section
          aria-label={t("progressLabel")}
          className="grid gap-3 rounded-lg border bg-card p-4"
        >
          <Progress found={s.counts.found} expected={s.counts.expected} />
          <dl className="grid grid-cols-3 gap-3 text-center">
            <Count label={t("counts.found")} value={s.counts.found} />
            <Count label={t("counts.missing")} value={s.counts.missing} />
            <Count label={t("counts.needsLook")} value={s.counts.needsLook} />
          </dl>
          {!open && <p className="text-xs text-muted-foreground">{t("frozenNote")}</p>}
        </section>
      )}

      {open && (
        <div className="grid gap-4 lg:grid-cols-[1fr_20rem]">
          <div className="grid content-start gap-3">
            <ScanInput
              id="stocktake-scan"
              label={t("scanLabel")}
              hint={t("scanHint")}
              placeholder={t("scanPlaceholder")}
              onScan={(code) => scan.mutate(code)}
              busy={scan.isPending}
            />
            <ResultPanel result={last} />
          </div>
          {recent.length > 0 && (
            <section aria-labelledby="recent-scans" className="grid content-start gap-2">
              <h2 id="recent-scans" className="text-sm font-semibold">
                {t("recent")}
              </h2>
              <ul className="grid gap-1 text-sm">
                {recent.map((r, i) => (
                  <li key={`${r.barcode}-${i}`} className="flex items-center gap-2">
                    <ResultIcon result={r} className="size-4 shrink-0" />
                    <span className="font-mono text-xs">{r.barcode}</span>
                    <span className="truncate text-muted-foreground">{r.title}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      )}

      {s.status !== "CANCELLED" && (
        <Tabs defaultValue="missing" className="gap-3">
          <TabsList>
            <TabsTrigger value="missing">
              {t("missingTab", { count: s.counts.missing })}
            </TabsTrigger>
            <TabsTrigger value="issues">
              {t("issuesTab", { count: s.counts.needsLook })}
            </TabsTrigger>
          </TabsList>
          <TabsContent value="missing">
            <MissingList id={id} />
          </TabsContent>
          <TabsContent value="issues">
            <IssuesList id={id} />
          </TabsContent>
        </Tabs>
      )}

      <Dialog open={finishing !== null} onOpenChange={(o) => !o && setFinishing(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {finishing === "complete" ? t("completeTitle") : t("cancelTitle")}
            </DialogTitle>
            <DialogDescription>
              {finishing === "complete"
                ? t("completeText", { missing: s.counts.missing })
                : t("cancelText")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setFinishing(null)}>
              {t("keepScanning")}
            </Button>
            <Button
              variant={finishing === "cancel" ? "destructive" : "default"}
              disabled={finish.isPending}
              onClick={() => finishing && finish.mutate(finishing)}
            >
              {finishing === "complete" ? t("complete") : t("cancelStocktake")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Count({ label, value }: { label: string; value: number }) {
  const format = useFormatter();
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="font-heading text-2xl font-semibold tabular-nums">{format.number(value)}</dd>
    </div>
  );
}

function ResultIcon({ result: r, className }: { result: ScanResult; className?: string }) {
  if (r.alreadyScanned)
    return <Repeat aria-hidden className={cn(className, "text-muted-foreground")} />;
  if (r.result === "FOUND")
    return <CircleCheck aria-hidden className={cn(className, "text-available-foreground")} />;
  if (r.result === "NOT_IN_SCOPE")
    return <CircleAlert aria-hidden className={cn(className, "text-on-loan-foreground")} />;
  if (r.result === "NOT_ON_SHELF")
    return <CircleX aria-hidden className={cn(className, "text-destructive")} />;
  return <CircleHelp aria-hidden className={cn(className, "text-destructive")} />;
}

/** Big and coloured, readable from a step away; read out by screen readers. */
function ResultPanel({ result: r }: { result: ScanResult | null }) {
  const t = useTranslations("Stocktake");
  const tStatus = useTranslations("Copies.statuses");
  const kind = !r ? null : r.alreadyScanned ? "again" : r.result;
  return (
    <div
      role="status"
      aria-live="assertive"
      className={cn(
        "grid min-h-32 content-center justify-items-center gap-1 rounded-lg border p-4 text-center",
        kind === "FOUND" && "border-available/40 bg-available/10",
        (kind === "NOT_IN_SCOPE" || kind === "again") && "border-on-loan/40 bg-on-loan/10",
        (kind === "NOT_ON_SHELF" || kind === "UNKNOWN") &&
          "border-destructive/40 bg-destructive/10",
      )}
    >
      {!r ? (
        <p className="text-muted-foreground">{t("waiting")}</p>
      ) : (
        <>
          <ResultIcon result={r} className="size-9" />
          <p className="text-lg font-semibold">{t(`results.${r.alreadyScanned ? "again" : r.result}`)}</p>
          {r.title && <p className="text-sm">{r.title}</p>}
          <p className="font-mono text-xs text-muted-foreground">
            {r.barcode}
            {r.shelfLocation && ` · ${r.shelfLocation}`}
          </p>
          {r.result === "NOT_ON_SHELF" && r.copyStatus && (
            <p className="text-sm">
              {t("systemSays", {
                status: tStatus.has(r.copyStatus as never)
                  ? tStatus(r.copyStatus as never)
                  : r.copyStatus,
              })}
            </p>
          )}
        </>
      )}
    </div>
  );
}

function MissingList({ id }: { id: string }) {
  const t = useTranslations("Stocktake");
  const tCommon = useTranslations("Common");
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: [...STOCKTAKE_KEY, "missing", id, page],
    queryFn: () =>
      api.get<Missing[]>(`/staff/stocktakes/${id}/missing`, {
        query: { page, size: MISSING_PAGE },
      }),
    placeholderData: keepPreviousData,
  });
  if (query.isPending) return <HexLoader label={tCommon("loading")} />;
  if (!query.data || query.data.length === 0) {
    return (
      <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("noneMissing")}</p>
    );
  }
  return (
    <div className="grid gap-2">
      <p className="text-xs text-muted-foreground">{t("missingHint")}</p>
      <div className="overflow-x-auto rounded-lg border">
        <DataTable
          headers={[t("shelf"), t("callNumber"), t("title"), t("barcode")]}
          numeric={[false, false, false, false]}
          rows={query.data.map((m) => [
            m.shelfLocation ?? "",
            m.callNumber ?? "",
            m.title,
            m.barcode,
          ])}
        />
      </div>
      {/* The API pages by offset; a full page means there may be more */}
      <Pager
        page={page}
        totalPages={query.data.length === MISSING_PAGE ? page + 2 : page + 1}
        onChange={setPage}
      />
    </div>
  );
}

function IssuesList({ id }: { id: string }) {
  const t = useTranslations("Stocktake");
  const tStatus = useTranslations("Copies.statuses");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const query = useQuery({
    queryKey: [...STOCKTAKE_KEY, "issues", id],
    queryFn: () => api.get<Issue[]>(`/staff/stocktakes/${id}/issues`),
  });
  if (query.isPending) return <HexLoader label={tCommon("loading")} />;
  if (!query.data || query.data.length === 0) {
    return (
      <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("noIssues")}</p>
    );
  }
  return (
    <div className="overflow-x-auto rounded-lg border">
      <DataTable
        headers={[t("when"), t("barcode"), t("title"), t("whatsWrong")]}
        numeric={[false, false, false, false]}
        rows={query.data.map((i) => [
          format.dateTime(new Date(i.scannedAt), { timeStyle: "short" }),
          i.barcode,
          [i.title, i.shelfLocation].filter(Boolean).join(" · "),
          t(`issues.${i.result}`, {
            status:
              i.copyStatus && tStatus.has(i.copyStatus as never)
                ? tStatus(i.copyStatus as never)
                : (i.copyStatus ?? ""),
          }),
        ])}
      />
    </div>
  );
}
