"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { addDays, libraryDay } from "@/features/events/library-dates";
import { usePathname, useRouter } from "@/i18n/navigation";

export const REPORTS = [
  "circulation",
  "popular",
  "weeding",
  "overdue",
  "fines",
  "membership",
] as const;
export type ReportName = (typeof REPORTS)[number];

export type GroupBy = "DAY" | "MONTH" | "CATEGORY";

/**
 * Which report, which days and how they're grouped, kept in the address (?report=fines&from=...)
 * so a view can be bookmarked or shared. Days are Sri Lankan library days. Each report has a
 * sensible default range; the range picker (step 374) changes from/to.
 */
export function useReportRange() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  // Today in Sri Lanka, read once
  const [today] = useState(() => libraryDay(new Date()));

  const asked = params.get("report");
  const report: ReportName = REPORTS.includes(asked as ReportName)
    ? (asked as ReportName)
    : "circulation";
  const day = (key: string) => {
    const v = params.get(key);
    return v && /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : null;
  };
  const defaults = defaultRange(report, today);
  const from = day("from") ?? defaults.from;
  const to = day("to") ?? defaults.to;
  const g = params.get("groupBy");
  const groupBy: GroupBy =
    g === "DAY" || g === "MONTH" || (g === "CATEGORY" && report === "circulation")
      ? g
      : defaults.groupBy;

  const set = (next: Record<string, string | null>) => {
    const query = new URLSearchParams(params);
    for (const [k, v] of Object.entries(next)) {
      if (v == null) query.delete(k);
      else query.set(k, v);
    }
    const search = query.toString();
    router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
  };

  return {
    report,
    from,
    to,
    groupBy,
    today,
    /** Another report starts from its own default range */
    setReport: (r: ReportName) => set({ report: r, from: null, to: null, groupBy: null }),
    /** Over a year can't be shown day by day (the API's limit): such a range switches to months */
    setRange: (f: string, t: string) =>
      set({
        from: f,
        to: t,
        groupBy: groupBy === "DAY" && daysBetween(f, t) > MAX_DAYS_BY_DAY ? "MONTH" : groupBy,
      }),
    setGroupBy: (value: GroupBy) => set({ groupBy: value }),
  };
}

/** The API shows at most this many days one by one (CirculationReportService). */
const MAX_DAYS_BY_DAY = 366;

function daysBetween(from: string, to: string) {
  return Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000) + 1;
}

/** Circulation: the last 30 days by day; money and members: by month over a year or two. */
function defaultRange(report: ReportName, today: string) {
  switch (report) {
    case "circulation":
      return { from: addDays(today, -29), to: today, groupBy: "DAY" as GroupBy };
    case "weeding":
      // "Not borrowed since": two years back, like the server's default
      return { from: addDays(today, -730), to: today, groupBy: "MONTH" as GroupBy };
    case "membership":
      return { from: firstOfMonth(addDays(today, -730)), to: today, groupBy: "MONTH" as GroupBy };
    default:
      return { from: firstOfMonth(addDays(today, -365)), to: today, groupBy: "MONTH" as GroupBy };
  }
}

function firstOfMonth(day: string) {
  return `${day.slice(0, 7)}-01`;
}
