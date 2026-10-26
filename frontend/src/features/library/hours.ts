"use client";

import { useQuery } from "@tanstack/react-query";
import { useFormatter, useTranslations } from "next-intl";
import { api, type Schema } from "@/lib/api";

export type Hours = Schema<"HoursResponse">;
export type DayName = Hours["week"][number]["day"];

const ISO_DAYS: DayName[] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

/** The library's hours and "open now", refreshed every minute (badge and visit page share it). */
export function useLibraryHours() {
  return useQuery({
    queryKey: ["library", "hours"],
    queryFn: () => api.get<Hours>("/library/hours"),
    refetchInterval: 60_000,
    staleTime: 30_000,
  });
}

/**
 * Formats the API's Sri Lankan local times ("08:00:00", "2026-10-27T08:00:00") in the reader's
 * language. They have no time zone, so they're shown as they are, not converted.
 */
export function useHoursFormat() {
  const format = useFormatter();
  const t = useTranslations("Library");

  // A fixed date in UTC, so only the time of day matters
  const time = (hhmmss: string) =>
    format.dateTime(new Date(`2000-01-03T${hhmmss}Z`), {
      hour: "numeric",
      minute: "2-digit",
      timeZone: "UTC",
    });

  // 2024-01-01 was a Monday: day n of the ISO week is 2024-01-0n
  const dayName = (day: DayName) =>
    format.dateTime(new Date(Date.UTC(2024, 0, ISO_DAYS.indexOf(day) + 1)), {
      weekday: "long",
      timeZone: "UTC",
    });

  /** "today at 8:00 AM", "tomorrow at 8:00 AM", "Monday at 8:00 AM" (library time). */
  const when = (localDateTime: string, libraryToday: string) => {
    const [date, clock] = localDateTime.split("T");
    const at = time(clock);
    const days = Math.round(
      (Date.parse(`${date}T00:00:00Z`) - Date.parse(`${libraryToday}T00:00:00Z`)) / 86_400_000,
    );
    if (days === 0) return t("today", { time: at });
    if (days === 1) return t("tomorrow", { time: at });
    const weekday = format.dateTime(new Date(`${date}T00:00:00Z`), {
      weekday: "long",
      timeZone: "UTC",
    });
    return t("onDay", { day: weekday, time: at });
  };

  return { time, dayName, when, isoDays: ISO_DAYS };
}

/** Today's date in Sri Lanka (yyyy-mm-dd), whatever time zone the reader's device is in. */
export function libraryToday() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Colombo" }).format(new Date());
}

/** The ISO weekday name of today in Sri Lanka. */
export function libraryWeekday(): DayName {
  const day = new Date(`${libraryToday()}T00:00:00Z`).getUTCDay(); // 0 = Sunday
  return ISO_DAYS[(day + 6) % 7];
}
