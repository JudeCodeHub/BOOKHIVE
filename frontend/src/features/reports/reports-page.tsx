"use client";

import { useState, type ReactNode } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FormAlert } from "@/features/auth/auth-card";
import { useErrorMessage } from "@/features/auth/errors";
import { dayAsDate } from "@/features/events/library-dates";
import { Pager } from "@/features/staff/pager";
import { useMoney } from "@/lib/formats";
import { api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";
import { BarList, ColumnChart, DataTable, NumbersTable, StatTile, TimeChart } from "./charts";
import { DateRangePicker, SincePicker } from "./date-range-picker";
import { REPORTS, useReportRange, type GroupBy, type ReportName } from "./report-range";

type Circulation = Schema<"CirculationReportResponse">;
type MostBorrowed = Schema<"MostBorrowedSummary">;
type NotBorrowed = Schema<"NotBorrowedReportResponse">;
type Overdue = Schema<"OverdueReportResponse">;
type Fines = Schema<"FineReportResponse">;
type Membership = Schema<"MembershipReportResponse">;

type Range = { from: string; to: string; groupBy: GroupBy };

/**
 * Reports for admins (RPT-2...RPT-5, checklist step 373): one tab per report, the range above
 * them all, and every chart's numbers also as a table. The range picker and downloads come in
 * step 374 (ReportFilters).
 */
export function ReportsPage() {
  const t = useTranslations("Reports");
  const r = useReportRange();
  const range: Range = { from: r.from, to: r.to, groupBy: r.groupBy };

  return (
    <div className="grid gap-4">
      <Tabs value={r.report} onValueChange={(v) => r.setReport(v as ReportName)}>
        <TabsList className="h-auto flex-wrap">
          {REPORTS.map((name) => (
            <TabsTrigger key={name} value={name}>
              {t(`tabs.${name}`)}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      <ReportFilters
        report={r.report}
        range={range}
        today={r.today}
        onRange={r.setRange}
        onGroupBy={r.setGroupBy}
      />

      {r.report === "circulation" && <CirculationReport range={range} />}
      {r.report === "popular" && <PopularReport range={range} />}
      {r.report === "weeding" && <WeedingReport since={range.from} />}
      {r.report === "overdue" && <OverdueReport />}
      {r.report === "fines" && <FinesReport range={range} />}
      {r.report === "membership" && <MembershipReport range={range} />}
    </div>
  );
}

/**
 * One row above the report (checklist step 374): its dates, how it's grouped, and downloads.
 * Every chart, number and table below follows it.
 */
function ReportFilters({
  report,
  range,
  today,
  onRange,
  onGroupBy,
}: {
  report: ReportName;
  range: Range;
  today: string;
  onRange: (from: string, to: string) => void;
  onGroupBy: (g: GroupBy) => void;
}) {
  const t = useTranslations("Reports");
  const groupings: GroupBy[] =
    report === "circulation"
      ? ["DAY", "MONTH", "CATEGORY"]
      : report === "fines" || report === "membership"
        ? ["DAY", "MONTH"]
        : [];

  return (
    <div className="flex flex-wrap items-center gap-3 text-sm">
      {report === "overdue" ? (
        <p className="text-muted-foreground">{t("asOfToday")}</p>
      ) : report === "weeding" ? (
        <SincePicker since={range.from} today={today} onChange={(d) => onRange(d, range.to)} />
      ) : (
        <DateRangePicker from={range.from} to={range.to} today={today} onChange={onRange} />
      )}
      {groupings.length > 0 && (
        <div role="group" aria-label={t("groupBy")} className="flex rounded-md border p-0.5">
          {groupings.map((g) => (
            <Button
              key={g}
              size="sm"
              variant={range.groupBy === g ? "secondary" : "ghost"}
              aria-pressed={range.groupBy === g}
              onClick={() => onGroupBy(g)}
            >
              {t(`groupings.${g}`)}
            </Button>
          ))}
        </div>
      )}
      <ExportButtons report={report} range={range} />
    </div>
  );
}

/** The API's name for each report's download. */
const EXPORT_PATHS: Record<ReportName, string> = {
  circulation: "circulation",
  popular: "most-borrowed",
  weeding: "not-borrowed",
  overdue: "overdue",
  fines: "fines",
  membership: "membership",
};

/** CSV for spreadsheets, PDF to print or send; headings in the language the admin is using. */
function ExportButtons({ report, range }: { report: ReportName; range: Range }) {
  const t = useTranslations("Reports");
  const locale = useLocale();
  const errorText = useErrorMessage("Reports");
  const [busy, setBusy] = useState<"CSV" | "PDF" | null>(null);

  async function download(format: "CSV" | "PDF") {
    const query: Record<string, string | number> = { format, lang: locale };
    if (report === "weeding") query.since = range.from;
    else if (report !== "overdue") {
      query.from = range.from;
      query.to = range.to;
      if (report !== "popular") {
        query.groupBy =
          report === "circulation" ? range.groupBy : range.groupBy === "DAY" ? "DAY" : "MONTH";
      }
    }
    setBusy(format);
    try {
      const blob = await api.getBlob(`/admin/reports/${EXPORT_PATHS[report]}/export`, { query });
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = `bookhive-${EXPORT_PATHS[report]}-${report === "overdue" ? "now" : `${range.from}-${range.to}`}.${format.toLowerCase()}`;
      link.click();
      URL.revokeObjectURL(link.href);
    } catch (error) {
      toast.error(errorText(error));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="ml-auto flex gap-2">
      {(["CSV", "PDF"] as const).map((format) => (
        <Button
          key={format}
          variant="outline"
          size="sm"
          disabled={busy !== null}
          onClick={() => void download(format)}
        >
          <Download aria-hidden />
          {busy === format ? t("downloading") : t(`download${format}`)}
        </Button>
      ))}
    </div>
  );
}

/** Loading, error, and "keep the old picture faded while the new one loads". */
function ReportFrame<T>({
  query,
  children,
}: {
  query: {
    isPending: boolean;
    isError: boolean;
    error: unknown;
    data?: T;
    isPlaceholderData?: boolean;
  };
  children: (data: T) => ReactNode;
}) {
  const tCommon = useTranslations("Common");
  const errorText = useErrorMessage("Reports");
  if (query.isPending) {
    return (
      <div className="flex justify-center py-12">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (query.isError || query.data === undefined)
    return <FormAlert>{errorText(query.error)}</FormAlert>;
  return (
    <div className={cn("grid gap-4 transition-opacity", query.isPlaceholderData && "opacity-60")}>
      {children(query.data)}
    </div>
  );
}

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="grid gap-3 rounded-lg border bg-card p-4">
      <h2 className="text-sm font-semibold">{title}</h2>
      {children}
    </section>
  );
}

function useLabels() {
  const format = useFormatter();
  const money = useMoney();
  const locale = useLocale();
  return {
    n: (x: number) => format.number(x),
    lkr: money,
    lkrShort: (x: number) => format.number(x, { maximumFractionDigits: 0 }),
    day: (d: string) => format.dateTime(dayAsDate(d), { day: "numeric", month: "short" }),
    month: (d: string) => format.dateTime(dayAsDate(d), { month: "short", year: "numeric" }),
    date: (d: string) => format.dateTime(dayAsDate(d), { dateStyle: "medium" }),
    pick: (en: string, si: string, ta: string) =>
      locale === "si" ? si : locale === "ta" ? ta : en,
  };
}

function CirculationReport({ range }: { range: Range }) {
  const t = useTranslations("Reports");
  const L = useLabels();
  const query = useQuery({
    queryKey: ["admin", "reports", "circulation", range],
    queryFn: () => api.get<Circulation>("/admin/reports/circulation", { query: range }),
    placeholderData: keepPreviousData,
  });
  const when = range.groupBy === "MONTH" ? L.month : L.day;

  return (
    <ReportFrame query={query}>
      {(r) => (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            <StatTile label={t("circ.loans")} value={L.n(r.totals.checkouts)} />
            <StatTile label={t("circ.returns")} value={L.n(r.totals.returns)} />
            <StatTile
              label={t("circ.borrowers")}
              value={L.n(r.totals.borrowers)}
              note={t("circ.borrowersNote")}
            />
          </div>
          {r.groupBy === "CATEGORY" ? (
            <Card title={t("circ.byCategory")}>
              <BarList
                label={t("circ.loans")}
                summary={t("circ.categorySummary", { count: r.categories.length })}
                rows={r.categories.map((c) => ({
                  name: `${c.ddcCode ?? ""} ${L.pick(c.nameEn, c.nameSi, c.nameTa)}`.trim(),
                  value: c.checkouts,
                }))}
              />
              {r.totals.uncategorised > 0 && (
                <p className="text-xs text-muted-foreground">
                  {t("circ.uncategorised", { count: r.totals.uncategorised })}
                </p>
              )}
              <NumbersTable
                headers={[t("circ.category"), t("circ.loans"), t("circ.borrowers")]}
                numeric={[false, true, true]}
                rows={r.categories.map((c) => [
                  `${c.ddcCode ?? ""} ${L.pick(c.nameEn, c.nameSi, c.nameTa)}`.trim(),
                  L.n(c.checkouts),
                  L.n(c.borrowers),
                ])}
              />
            </Card>
          ) : (
            <Card title={t("circ.overTime")}>
              <TimeChart
                data={r.periods}
                xKey="start"
                xLabel={when}
                value={L.n}
                series={[
                  { key: "checkouts", label: t("circ.loans") },
                  { key: "returns", label: t("circ.returns") },
                ]}
                summary={t("circ.timeSummary", {
                  loans: r.totals.checkouts,
                  returns: r.totals.returns,
                })}
              />
              <NumbersTable
                headers={[
                  t(range.groupBy === "MONTH" ? "month" : "day"),
                  t("circ.loans"),
                  t("circ.returns"),
                  t("circ.borrowers"),
                ]}
                numeric={[false, true, true, true]}
                rows={r.periods.map((p) => [
                  range.groupBy === "MONTH" ? L.month(p.start) : L.date(p.start),
                  L.n(p.checkouts),
                  L.n(p.returns),
                  L.n(p.borrowers),
                ])}
              />
            </Card>
          )}
        </>
      )}
    </ReportFrame>
  );
}

function PopularReport({ range }: { range: Range }) {
  const t = useTranslations("Reports");
  const L = useLabels();
  const query = useQuery({
    queryKey: ["admin", "reports", "most-borrowed", range.from, range.to],
    queryFn: () =>
      api.get<MostBorrowed[]>("/admin/reports/most-borrowed", {
        query: { from: range.from, to: range.to, limit: 50 },
      }),
    placeholderData: keepPreviousData,
  });
  return (
    <ReportFrame query={query}>
      {(rows) =>
        rows.length === 0 ? (
          <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("noLoans")}</p>
        ) : (
          <>
            <Card title={t("popular.top10")}>
              <BarList
                label={t("circ.loans")}
                summary={t("popular.summary", { title: rows[0].title, count: rows[0].checkouts })}
                rows={rows.slice(0, 10).map((b) => ({
                  name: b.title.length > 32 ? `${b.title.slice(0, 31)}…` : b.title,
                  value: b.checkouts,
                }))}
              />
            </Card>
            <Card title={t("popular.all", { count: rows.length })}>
              <div className="overflow-x-auto">
                <DataTable
                  headers={[
                    "#",
                    t("book.title"),
                    t("book.authors"),
                    t("book.callNumber"),
                    t("book.copies"),
                    t("circ.loans"),
                    t("popular.readers"),
                  ]}
                  numeric={[true, false, false, false, true, true, true]}
                  rows={rows.map((b, i) => [
                    i + 1,
                    b.title,
                    b.authors ?? "",
                    b.callNumber ?? "",
                    L.n(b.copies),
                    L.n(b.checkouts),
                    L.n(b.borrowers),
                  ])}
                />
              </div>
            </Card>
          </>
        )
      }
    </ReportFrame>
  );
}

function WeedingReport({ since }: { since: string }) {
  const t = useTranslations("Reports");
  const L = useLabels();
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ["admin", "reports", "not-borrowed", since, page],
    queryFn: () =>
      api.get<NotBorrowed>("/admin/reports/not-borrowed", {
        query: { since, page, size: 50 },
      }),
    placeholderData: keepPreviousData,
  });
  return (
    <ReportFrame query={query}>
      {(r) => (
        <>
          <StatTile
            label={t("weeding.count")}
            value={L.n(r.totalElements)}
            note={t("weeding.note")}
          />
          {r.totalElements > 0 && (
            <Card title={t("weeding.list")}>
              <div className="overflow-x-auto">
                <DataTable
                  headers={[
                    t("book.title"),
                    t("book.authors"),
                    t("book.callNumber"),
                    t("book.copies"),
                    t("weeding.onShelfSince"),
                    t("weeding.lastBorrowed"),
                  ]}
                  numeric={[false, false, false, true, false, false]}
                  rows={r.books.map((b) => [
                    b.title,
                    b.authors ?? "",
                    b.callNumber ?? "",
                    L.n(b.copies),
                    L.date(b.inStockSince),
                    b.lastBorrowedOn ? L.date(b.lastBorrowedOn) : t("weeding.never"),
                  ])}
                />
              </div>
              <Pager page={page} totalPages={r.totalPages} onChange={setPage} />
            </Card>
          )}
        </>
      )}
    </ReportFrame>
  );
}

function OverdueReport() {
  const t = useTranslations("Reports");
  const L = useLabels();
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ["admin", "reports", "overdue", page],
    queryFn: () => api.get<Overdue>("/admin/reports/overdue", { query: { page, size: 50 } }),
    placeholderData: keepPreviousData,
  });
  return (
    <ReportFrame query={query}>
      {(r) => (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile label={t("overdue.upToWeek")} value={L.n(r.totals.upToWeek)} />
            <StatTile label={t("overdue.upToMonth")} value={L.n(r.totals.upToMonth)} />
            <StatTile label={t("overdue.overMonth")} value={L.n(r.totals.overMonth)} />
            <StatTile label={t("overdue.owed")} value={L.lkr(r.totals.finesOwed)} />
          </div>
          {r.totals.total === 0 ? (
            <p className="rounded-lg border p-6 text-center text-muted-foreground">
              {t("overdue.none")}
            </p>
          ) : (
            <Card title={t("overdue.list", { count: r.totals.total })}>
              <div className="overflow-x-auto">
                <DataTable
                  headers={[
                    t("overdue.member"),
                    t("overdue.contact"),
                    t("book.title"),
                    t("overdue.due"),
                    t("overdue.daysLate"),
                    t("overdue.fine"),
                  ]}
                  numeric={[false, false, false, false, true, true]}
                  rows={r.loans.map((l) => [
                    `${l.memberName} (${l.memberNo})`,
                    [l.phone, l.email].filter(Boolean).join(" · "),
                    `${l.title} · ${l.barcode}`,
                    L.date(l.dueDate),
                    L.n(l.daysOverdue),
                    L.lkr(l.fineOwed),
                  ])}
                />
              </div>
              <Pager page={page} totalPages={r.totalPages} onChange={setPage} />
            </Card>
          )}
        </>
      )}
    </ReportFrame>
  );
}

function FinesReport({ range }: { range: Range }) {
  const t = useTranslations("Reports");
  const L = useLabels();
  const groupBy = range.groupBy === "DAY" ? "DAY" : "MONTH";
  const query = useQuery({
    queryKey: ["admin", "reports", "fines", range.from, range.to, groupBy],
    queryFn: () =>
      api.get<Fines>("/admin/reports/fines", {
        query: { from: range.from, to: range.to, groupBy },
      }),
    placeholderData: keepPreviousData,
  });
  const when = groupBy === "MONTH" ? L.month : L.day;
  return (
    <ReportFrame query={query}>
      {(r) => (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile label={t("fines.charged")} value={L.lkr(r.totals.charged)} />
            <StatTile
              label={t("fines.collected")}
              value={L.lkr(r.totals.collected)}
              note={t("fines.collectedNote", {
                cash: L.lkr(r.totals.collectedCash),
                online: L.lkr(r.totals.collectedOnline),
              })}
            />
            <StatTile label={t("fines.waived")} value={L.lkr(r.totals.waived)} />
            <StatTile label={t("fines.refunded")} value={L.lkr(r.totals.refunded)} />
          </div>
          <Card title={t("fines.overTime")}>
            <TimeChart
              data={r.periods.map((p) => ({
                ...p,
                collected: p.collectedCash + p.collectedOnline - p.refunded,
              }))}
              xKey="start"
              xLabel={when}
              value={L.lkrShort}
              series={[
                { key: "charged", label: t("fines.charged") },
                { key: "collected", label: t("fines.collected") },
                { key: "waived", label: t("fines.waived") },
              ]}
              summary={t("fines.summary", {
                charged: L.lkr(r.totals.charged),
                collected: L.lkr(r.totals.collected),
              })}
            />
            <NumbersTable
              headers={[
                t(groupBy === "MONTH" ? "month" : "day"),
                t("fines.charged"),
                t("fines.cash"),
                t("fines.online"),
                t("fines.refunded"),
                t("fines.waived"),
              ]}
              numeric={[false, true, true, true, true, true]}
              rows={r.periods.map((p) => [
                groupBy === "MONTH" ? L.month(p.start) : L.date(p.start),
                L.lkr(p.charged),
                L.lkr(p.collectedCash),
                L.lkr(p.collectedOnline),
                L.lkr(p.refunded),
                L.lkr(p.waived),
              ])}
            />
          </Card>
          <Card title={t("fines.byType")}>
            <DataTable
              headers={[
                t("fines.type"),
                t("fines.charged"),
                t("fines.collected"),
                t("fines.waived"),
              ]}
              numeric={[false, true, true, true]}
              rows={r.byType.map((k) => [
                t(`fines.types.${k.type}` as never),
                L.lkr(k.charged),
                L.lkr(k.collected),
                L.lkr(k.waived),
              ])}
            />
          </Card>
        </>
      )}
    </ReportFrame>
  );
}

function MembershipReport({ range }: { range: Range }) {
  const t = useTranslations("Reports");
  const L = useLabels();
  const groupBy = range.groupBy === "DAY" ? "DAY" : "MONTH";
  const query = useQuery({
    queryKey: ["admin", "reports", "membership", range.from, range.to, groupBy],
    queryFn: () =>
      api.get<Membership>("/admin/reports/membership", {
        query: { from: range.from, to: range.to, groupBy },
      }),
    placeholderData: keepPreviousData,
  });
  const when = groupBy === "MONTH" ? L.month : L.day;
  return (
    <ReportFrame query={query}>
      {(r) => (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            <StatTile label={t("members.activeNow")} value={L.n(r.totals.activeNow)} />
            <StatTile label={t("members.newMembers")} value={L.n(r.totals.newMembers)} />
            <StatTile label={t("members.waiting")} value={L.n(r.totals.pendingNow)} />
          </div>
          <Card title={t("members.overTime")}>
            <TimeChart
              data={r.periods}
              xKey="start"
              xLabel={when}
              value={L.n}
              series={[{ key: "membersAtEnd", label: t("members.members") }]}
              summary={t("members.summary", {
                first: r.periods[0]?.membersAtEnd ?? 0,
                last: r.periods.at(-1)?.membersAtEnd ?? 0,
              })}
            />
            <p className="text-xs text-muted-foreground">{t("members.note")}</p>
          </Card>
          <Card title={t("members.newPerPeriod")}>
            <ColumnChart
              data={r.periods}
              xKey="start"
              xLabel={when}
              yKey="newMembers"
              label={t("members.newMembers")}
              value={L.n}
              summary={t("members.newSummary", { count: r.totals.newMembers })}
            />
            <NumbersTable
              headers={[
                t(groupBy === "MONTH" ? "month" : "day"),
                t("members.applications"),
                t("members.newMembers"),
                t("members.members"),
              ]}
              numeric={[false, true, true, true]}
              rows={r.periods.map((p) => [
                groupBy === "MONTH" ? L.month(p.start) : L.date(p.start),
                L.n(p.applications),
                L.n(p.newMembers),
                L.n(p.membersAtEnd),
              ])}
            />
          </Card>
          <Card title={t("members.byType")}>
            <DataTable
              headers={[t("members.type"), t("members.newMembers"), t("members.activeNow")]}
              numeric={[false, true, true]}
              rows={r.byType.map((k) => [
                L.pick(k.nameEn, k.nameSi, k.nameTa),
                L.n(k.newMembers),
                L.n(k.activeNow),
              ])}
            />
          </Card>
        </>
      )}
    </ReportFrame>
  );
}
