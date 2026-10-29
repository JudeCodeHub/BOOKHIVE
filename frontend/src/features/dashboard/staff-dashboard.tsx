"use client";

import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  CalendarDays,
  CircleAlert,
  Lightbulb,
  ScanBarcode,
  Star,
  UserCheck,
} from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { dayAsDate } from "@/features/events/library-dates";
import { NumbersTable, TimeChart } from "@/features/reports/charts";
import { Link } from "@/i18n/navigation";
import { useMoney } from "@/lib/formats";
import { api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type Dashboard = Schema<"StaffDashboardResponse">;

/** Today's numbers are read again this often, so a desk left open stays current. */
const REFRESH_MS = 60_000;

/**
 * The staff home page (RPT-1, checklist step 376): what happened at the desk today, what's
 * waiting on the hold shelf, what needs a decision, the day's fines, and the last two weeks of
 * borrowing. Every card leads to the page where the work is done.
 */
export function StaffDashboard() {
  const t = useTranslations("StaffHome");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const money = useMoney();

  const query = useQuery({
    queryKey: ["staff", "dashboard"],
    queryFn: () => api.get<Dashboard>("/staff/dashboard"),
    refetchInterval: REFRESH_MS,
  });

  if (query.isPending) {
    return (
      <div className="flex justify-center py-12">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (query.isError) return <FormAlert>{errorMessage(query.error)}</FormAlert>;

  const d = query.data;
  const n = (x: number) => format.number(x);
  const toDo = d.toDo.applications + d.toDo.reviews + d.toDo.suggestions;

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">
            {format.dateTime(dayAsDate(d.today), { dateStyle: "full" })}
          </p>
        </div>
        <Button asChild>
          <Link href="/staff/desk">
            <ScanBarcode aria-hidden />
            {t("goToDesk")}
          </Link>
        </Button>
      </div>

      <Section title={t("desk.title")}>
        <Tile label={t("desk.lent")} value={n(d.desk.checkedOut)} href="/staff/desk" />
        <Tile label={t("desk.returned")} value={n(d.desk.returned)} href="/staff/desk" />
        <Tile
          label={t("desk.dueToday")}
          value={n(d.desk.dueToday)}
          href="/staff/loans?view=dueSoon"
        />
        <Tile
          label={t("desk.overdue")}
          value={n(d.desk.overdue)}
          href="/staff/loans?view=overdue"
          attention={d.desk.overdue > 0}
          icon={d.desk.overdue > 0 ? <CircleAlert aria-hidden className="size-4" /> : undefined}
        />
      </Section>

      <Section title={t("holds.title")}>
        <Tile label={t("holds.ready")} value={n(d.holds.ready)} href="/staff/holds" />
        <Tile
          label={t("holds.lastDay")}
          value={n(d.holds.expiringToday)}
          href="/staff/holds"
          attention={d.holds.expiringToday > 0}
        />
        <Tile label={t("holds.waiting")} value={n(d.holds.waiting)} href="/staff/holds" />
        {d.eventsToday > 0 && (
          <Tile
            label={t("eventsToday")}
            value={n(d.eventsToday)}
            href="/staff/events"
            icon={<CalendarDays aria-hidden className="size-4" />}
          />
        )}
      </Section>

      <section aria-labelledby="to-do" className="grid gap-3">
        <h2 id="to-do" className="text-sm font-semibold">
          {t("toDo.title")}
        </h2>
        {toDo === 0 ? (
          <p className="rounded-lg border bg-card p-4 text-sm text-muted-foreground">
            {t("toDo.allClear")}
          </p>
        ) : (
          <ul className="grid gap-2 sm:grid-cols-3">
            <ToDoRow
              count={d.toDo.applications}
              label={t("toDo.applications", { count: d.toDo.applications })}
              href="/staff/applications"
              icon={<UserCheck aria-hidden className="size-4" />}
            />
            <ToDoRow
              count={d.toDo.reviews}
              label={t("toDo.reviews", { count: d.toDo.reviews })}
              href="/staff/reviews"
              icon={<Star aria-hidden className="size-4" />}
            />
            <ToDoRow
              count={d.toDo.suggestions}
              label={t("toDo.suggestions", { count: d.toDo.suggestions })}
              href="/staff/suggestions"
              icon={<Lightbulb aria-hidden className="size-4" />}
            />
          </ul>
        )}
      </section>

      <Section title={t("fines.title")}>
        <Tile label={t("fines.collected")} value={money(d.fines.collected)} />
        <Tile label={t("fines.waived")} value={money(d.fines.waived)} />
        <Tile
          label={t("fines.outstanding")}
          value={money(d.fines.outstanding)}
          note={t("fines.outstandingNote")}
        />
      </Section>

      <section aria-labelledby="two-weeks" className="grid gap-3 rounded-lg border bg-card p-4">
        <h2 id="two-weeks" className="text-sm font-semibold">
          {t("chart.title")}
        </h2>
        <TimeChart
          data={d.lastTwoWeeks}
          xKey="day"
          xLabel={(x) => format.dateTime(dayAsDate(x), { day: "numeric", month: "short" })}
          value={n}
          series={[
            { key: "checkouts", label: t("chart.loans") },
            { key: "returns", label: t("chart.returns") },
          ]}
          summary={t("chart.summary", {
            loans: d.lastTwoWeeks.reduce((sum, day) => sum + day.checkouts, 0),
            returns: d.lastTwoWeeks.reduce((sum, day) => sum + day.returns, 0),
          })}
          height={220}
        />
        <NumbersTable
          headers={[t("chart.day"), t("chart.loans"), t("chart.returns")]}
          numeric={[false, true, true]}
          rows={d.lastTwoWeeks.map((day) => [
            format.dateTime(dayAsDate(day.day), { dateStyle: "medium" }),
            n(day.checkouts),
            n(day.returns),
          ])}
        />
      </section>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  const id = title.replace(/\W+/g, "-").toLowerCase();
  return (
    <section aria-labelledby={id} className="grid gap-3">
      <h2 id={id} className="text-sm font-semibold">
        {title}
      </h2>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{children}</div>
    </section>
  );
}

/** A headline number. With `href` the whole tile is one link to where that work is done. */
function Tile({
  label,
  value,
  href,
  note,
  icon,
  attention = false,
}: {
  label: string;
  value: string;
  href?: string;
  note?: string;
  icon?: ReactNode;
  attention?: boolean;
}) {
  const inside = (
    <>
      <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
        {icon}
        {label}
      </p>
      <p
        className={cn(
          "font-heading text-2xl font-semibold tabular-nums",
          attention && "text-overdue-foreground",
        )}
      >
        {value}
      </p>
      {note && <p className="text-xs text-muted-foreground">{note}</p>}
    </>
  );
  const className = cn(
    "grid content-start gap-1 rounded-lg border bg-card p-4",
    attention && "border-overdue/40 bg-overdue/10",
  );
  return href ? (
    <Link href={href} className={cn(className, "transition-shadow hover:shadow-md")}>
      {inside}
    </Link>
  ) : (
    <div className={className}>{inside}</div>
  );
}

/** One thing waiting for staff; nothing to do is shown in grey, not hidden. */
function ToDoRow({
  count,
  label,
  href,
  icon,
}: {
  count: number;
  label: string;
  href: string;
  icon: ReactNode;
}) {
  return (
    <li>
      <Link
        href={href}
        className={cn(
          "flex items-center gap-2 rounded-lg border p-3 text-sm transition-shadow hover:shadow-md",
          count > 0 ? "bg-card font-medium" : "bg-card/50 text-muted-foreground",
        )}
      >
        {icon}
        <span className="flex-1">{label}</span>
        {count > 0 && <ArrowRight aria-hidden className="size-4" />}
      </Link>
    </li>
  );
}
