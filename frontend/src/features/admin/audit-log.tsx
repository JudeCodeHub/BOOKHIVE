"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { FilterX } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useFormatter, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { FormAlert } from "@/features/auth/auth-card";
import { useErrorMessage } from "@/features/auth/errors";
import { Pager } from "@/features/staff/pager";
import { usePathname, useRouter } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";

type Entry = Schema<"AuditEntrySummary">;
type Staff = Schema<"StaffSummary">;

const PAGE_SIZE = 50;
const FILTERS = ["actorId", "action", "entityType", "entityId", "from", "to"] as const;
type FilterName = (typeof FILTERS)[number];

/**
 * The audit log (ADM-5, checklist step 372): who did what and when, newest first. Filters live
 * in the address (?action=fine.&from=...), so a view can be bookmarked or sent to another admin;
 * clicking a person or an item narrows the list to them.
 */
export function AuditLog() {
  const t = useTranslations("AdminAudit");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorText = useErrorMessage("AdminAudit");
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const filters = Object.fromEntries(FILTERS.map((f) => [f, params.get(f) ?? ""])) as Record<
    FilterName,
    string
  >;
  const page = Math.max(0, Number(params.get("page") ?? 1) - 1) || 0;
  const active = FILTERS.filter((f) => filters[f] !== "");

  const go = (next: Partial<Record<FilterName | "page", string | null>>) => {
    const query = new URLSearchParams(params);
    for (const [key, value] of Object.entries(next)) {
      if (!value) query.delete(key);
      else query.set(key, value);
    }
    // A new filter starts from the first page
    if (!("page" in next)) query.delete("page");
    const search = query.toString();
    router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
  };

  const entries = useQuery({
    queryKey: ["admin", "audit", filters, page],
    queryFn: () =>
      api.get<PageResponse<Entry>>("/admin/audit", {
        query: {
          ...Object.fromEntries(active.map((f) => [f, filters[f]])),
          page,
          size: PAGE_SIZE,
        },
      }),
    placeholderData: keepPreviousData,
  });
  const actions = useQuery({
    queryKey: ["admin", "audit", "actions"],
    queryFn: () => api.get<string[]>("/admin/audit/actions"),
    staleTime: 5 * 60_000,
  });
  const staff = useQuery({
    queryKey: ["admin", "staff"],
    queryFn: () => api.get<Staff[]>("/admin/staff"),
  });

  const area = (action: string) => {
    const key = action.split(".")[0];
    return t.has(`areas.${key}` as never) ? t(`areas.${key}` as never) : key;
  };
  const areas = [...new Set((actions.data ?? []).map((a) => a.split(".")[0]))];
  const selectClass = "h-8 w-full rounded-lg border bg-transparent px-2.5 text-sm";
  // A person filtered by clicking a row may not be staff (e.g. a member): show their name
  const filteredPerson = entries.data?.content.find((e) => e.actorId === filters.actorId);

  return (
    <div className="grid gap-4">
      <div className="grid gap-3 rounded-lg border bg-card p-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="grid gap-1.5">
          <Label htmlFor="audit-action">{t("action")}</Label>
          <select
            id="audit-action"
            className={selectClass}
            value={filters.action}
            onChange={(e) => go({ action: e.target.value })}
          >
            <option value="">{t("anyAction")}</option>
            {areas.map((a) => (
              <optgroup key={a} label={area(a)}>
                <option value={`${a}.`}>{t("wholeArea", { area: area(a) })}</option>
                {(actions.data ?? [])
                  .filter((x) => x.startsWith(`${a}.`))
                  .map((x) => (
                    <option key={x} value={x}>
                      {x}
                    </option>
                  ))}
              </optgroup>
            ))}
          </select>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="audit-person">{t("person")}</Label>
          <select
            id="audit-person"
            className={selectClass}
            value={filters.actorId}
            onChange={(e) => go({ actorId: e.target.value })}
          >
            <option value="">{t("anyone")}</option>
            {filters.actorId && !staff.data?.some((s) => s.id === filters.actorId) && (
              <option value={filters.actorId}>
                {filteredPerson?.actorName ?? t("chosenPerson")}
              </option>
            )}
            {(staff.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {s.fullName}
              </option>
            ))}
          </select>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="audit-from">{t("from")}</Label>
          <Input
            id="audit-from"
            type="date"
            value={filters.from}
            max={filters.to || undefined}
            onChange={(e) => go({ from: e.target.value })}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="audit-to">{t("to")}</Label>
          <Input
            id="audit-to"
            type="date"
            value={filters.to}
            min={filters.from || undefined}
            onChange={(e) => go({ to: e.target.value })}
          />
        </div>
        {(filters.entityType || active.length > 0) && (
          <div className="flex flex-wrap items-center gap-2 text-sm sm:col-span-2 lg:col-span-4">
            {filters.entityType && (
              <span className="rounded bg-muted px-2 py-0.5 font-mono text-xs">
                {t("onItem", { type: filters.entityType, id: filters.entityId || "*" })}
              </span>
            )}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => go(Object.fromEntries(FILTERS.map((f) => [f, null])))}
            >
              <FilterX aria-hidden />
              {t("clearFilters")}
            </Button>
          </div>
        )}
      </div>

      {entries.isPending && <HexLoader label={tCommon("loading")} />}
      {entries.isError && <FormAlert>{errorText(entries.error)}</FormAlert>}
      {entries.data && (
        <>
          <p className="text-sm text-muted-foreground" aria-live="polite">
            {t("count", { count: entries.data.totalElements })}
          </p>
          {entries.data.totalElements === 0 ? (
            <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("empty")}</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("when")}</TableHead>
                  <TableHead>{t("person")}</TableHead>
                  <TableHead>{t("action")}</TableHead>
                  <TableHead>{t("item")}</TableHead>
                  <TableHead>{t("details")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {entries.data.content.map((e) => (
                  <TableRow key={e.id} className="align-top">
                    <TableCell className="text-sm whitespace-nowrap tabular-nums">
                      {format.dateTime(new Date(e.occurredAt), {
                        dateStyle: "medium",
                        timeStyle: "medium",
                      })}
                      {e.ip && (
                        <span className="block font-mono text-xs text-muted-foreground">
                          {e.ip}
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-sm">
                      {e.actorId ? (
                        <button
                          type="button"
                          className="text-left underline-offset-4 hover:underline"
                          title={t("onlyThisPerson")}
                          onClick={() => go({ actorId: e.actorId })}
                        >
                          {e.actorName ?? t("deletedAccount")}
                          {e.actorEmail && (
                            <span className="block text-xs text-muted-foreground">
                              {e.actorEmail}
                            </span>
                          )}
                        </button>
                      ) : (
                        <span className="text-muted-foreground">{t("system")}</span>
                      )}
                    </TableCell>
                    <TableCell className="text-sm">
                      {area(e.action)}
                      <span className="block font-mono text-xs text-muted-foreground">
                        {e.action}
                      </span>
                    </TableCell>
                    <TableCell className="text-sm">
                      {e.entityType ? (
                        <button
                          type="button"
                          className="text-left font-mono text-xs underline-offset-4 hover:underline"
                          title={t("onlyThisItem")}
                          onClick={() =>
                            go({ entityType: e.entityType, entityId: e.entityId ?? null })
                          }
                        >
                          {e.entityType}
                          {e.entityId && (
                            <span className="block break-all text-muted-foreground">
                              {e.entityId}
                            </span>
                          )}
                        </button>
                      ) : (
                        "–"
                      )}
                    </TableCell>
                    <TableCell className="max-w-md text-xs">
                      <Details details={e.details} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          <Pager
            page={page}
            totalPages={entries.data.totalPages}
            onChange={(p) => go({ page: p > 0 ? String(p + 1) : null })}
          />
        </>
      )}
    </div>
  );
}

/** The entry's extra facts as "key: value" lines (nested values as compact JSON). */
function Details({ details }: { details: Record<string, unknown> }) {
  const keys = Object.keys(details ?? {});
  if (keys.length === 0) return <span className="text-muted-foreground">–</span>;
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-2 gap-y-0.5">
      {keys.map((k) => {
        const value = details[k];
        return (
          <div key={k} className="contents">
            <dt className="text-muted-foreground">{k}</dt>
            <dd className="font-mono break-all">
              {typeof value === "string" ? value : JSON.stringify(value)}
            </dd>
          </div>
        );
      })}
    </dl>
  );
}
