"use client";

import { useState } from "react";
import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { ClipboardCheck } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormAlert } from "@/features/auth/auth-card";
import { useErrorMessage } from "@/features/auth/errors";
import { Pager } from "@/features/staff/pager";
import { Link, useRouter } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";
import { Progress } from "./progress";

export type Stocktake = Schema<"StocktakeSummary">;
type CategoryNode = Schema<"CategoryTreeResponse">;

export const STOCKTAKE_KEY = ["staff", "stocktakes"] as const;

/** Categories as a flat list with depth, for an indented <select>. */
function flatten(nodes: CategoryNode[], depth = 0): { node: CategoryNode; depth: number }[] {
  return nodes.flatMap((node) => [{ node, depth }, ...flatten(node.children, depth + 1)]);
}

/** Stock-takes (RPT-6, checklist step 375): newest first, and starting a new one. */
export function StocktakeList() {
  const t = useTranslations("Stocktake");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorText = useErrorMessage("Stocktake");
  const [page, setPage] = useState(0);
  const [starting, setStarting] = useState(false);

  const query = useQuery({
    queryKey: [...STOCKTAKE_KEY, page],
    queryFn: () =>
      api.get<PageResponse<Stocktake>>("/staff/stocktakes", { query: { page, size: 20 } }),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="grid gap-4">
      <div>
        <Button onClick={() => setStarting(true)}>
          <ClipboardCheck aria-hidden />
          {t("start")}
        </Button>
      </div>
      {query.isPending && <HexLoader label={tCommon("loading")} />}
      {query.isError && <FormAlert>{errorText(query.error)}</FormAlert>}
      {query.data && query.data.totalElements === 0 && (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("empty")}</p>
      )}
      {query.data && query.data.totalElements > 0 && (
        <>
          <ul className="grid gap-3">
            {query.data.content.map((s) => (
              <li key={s.id}>
                <Link
                  href={`/staff/stocktake/${s.id}`}
                  className="grid gap-2 rounded-lg border bg-card p-4 hover:shadow-md"
                >
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{s.name}</span>
                    <StatusBadge status={s.status} />
                    <span className="ml-auto text-xs text-muted-foreground">
                      {format.dateTime(new Date(s.startedAt), {
                        dateStyle: "medium",
                        timeStyle: "short",
                      })}
                    </span>
                  </div>
                  <p className="text-sm text-muted-foreground">
                    <Scope stocktake={s} />
                  </p>
                  {s.status !== "CANCELLED" && (
                    <Progress found={s.counts.found} expected={s.counts.expected} compact />
                  )}
                </Link>
              </li>
            ))}
          </ul>
          <Pager page={page} totalPages={query.data.totalPages} onChange={setPage} />
        </>
      )}
      <Dialog open={starting} onOpenChange={setStarting}>
        <DialogContent>{starting && <StartForm onDone={() => setStarting(false)} />}</DialogContent>
      </Dialog>
    </div>
  );
}

export function StatusBadge({ status }: { status: Stocktake["status"] }) {
  const t = useTranslations("Stocktake.statuses");
  return (
    <Badge
      variant={status === "OPEN" ? "on-loan" : status === "COMPLETED" ? "available" : "outline"}
    >
      {t(status)}
    </Badge>
  );
}

/** "Shelves starting with …, in category …" or "The whole library". */
export function Scope({ stocktake: s }: { stocktake: Stocktake }) {
  const t = useTranslations("Stocktake");
  const locale = useLocale();
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: () => api.get<CategoryNode[]>("/categories"),
    staleTime: 60 * 60_000,
    enabled: s.categoryId != null,
  });
  const node = flatten(categories.data ?? []).find((x) => x.node.id === s.categoryId)?.node;
  const category = node
    ? locale === "si"
      ? node.nameSi
      : locale === "ta"
        ? node.nameTa
        : node.nameEn
    : null;
  if (!s.categoryId && !s.shelfPrefix) return <>{t("wholeLibrary")}</>;
  return (
    <>
      {[
        s.shelfPrefix && t("scopeShelves", { shelves: s.shelfPrefix }),
        s.categoryId && t("scopeCategory", { category: category ?? "…" }),
      ]
        .filter(Boolean)
        .join(" · ")}
    </>
  );
}

function StartForm({ onDone }: { onDone: () => void }) {
  const t = useTranslations("Stocktake");
  const locale = useLocale();
  const format = useFormatter();
  const errorText = useErrorMessage("Stocktake");
  const queryClient = useQueryClient();
  const router = useRouter();
  const [name, setName] = useState(() =>
    t("defaultName", { date: format.dateTime(new Date(), { dateStyle: "medium" }) }),
  );
  const [categoryId, setCategoryId] = useState("");
  const [shelfPrefix, setShelfPrefix] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: () => api.get<CategoryNode[]>("/categories"),
    staleTime: 60 * 60_000,
  });
  const label = (n: CategoryNode) =>
    locale === "si" ? n.nameSi : locale === "ta" ? n.nameTa : n.nameEn;

  return (
    <form
      className="grid gap-4"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
          const created = await api.post<Stocktake>("/staff/stocktakes", {
            name: name.trim(),
            categoryId: categoryId || undefined,
            shelfPrefix: shelfPrefix.trim() || undefined,
          });
          void queryClient.invalidateQueries({ queryKey: STOCKTAKE_KEY });
          onDone();
          router.push(`/staff/stocktake/${created.id}`);
        } catch (err) {
          setError(errorText(err));
        } finally {
          setBusy(false);
        }
      }}
    >
      <DialogHeader>
        <DialogTitle>{t("startTitle")}</DialogTitle>
        <DialogDescription>{t("startText")}</DialogDescription>
      </DialogHeader>
      {error && <FormAlert>{error}</FormAlert>}
      <div className="grid gap-1.5">
        <Label htmlFor="st-name">{t("name")}</Label>
        <Input
          id="st-name"
          value={name}
          maxLength={120}
          onChange={(e) => setName(e.target.value)}
          required
        />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="st-shelf">{t("shelves")}</Label>
        <Input
          id="st-shelf"
          value={shelfPrefix}
          maxLength={100}
          placeholder={t("shelvesPlaceholder")}
          onChange={(e) => setShelfPrefix(e.target.value)}
        />
        <p className="text-xs text-muted-foreground">{t("shelvesHint")}</p>
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="st-category">{t("category")}</Label>
        <select
          id="st-category"
          className="h-8 rounded-lg border bg-transparent px-2.5 text-sm"
          value={categoryId}
          onChange={(e) => setCategoryId(e.target.value)}
        >
          <option value="">{t("anyCategory")}</option>
          {flatten(categories.data ?? []).map(({ node, depth }) => (
            <option key={node.id} value={node.id}>
              {"  ".repeat(depth)}
              {node.ddcCode ? `${node.ddcCode} ` : ""}
              {label(node)}
            </option>
          ))}
        </select>
        <p className="text-xs text-muted-foreground">{t("categoryHint")}</p>
      </div>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>
          {t("cancel")}
        </Button>
        <Button type="submit" disabled={busy || !name.trim()}>
          {t("startScanning")}
        </Button>
      </DialogFooter>
    </form>
  );
}
