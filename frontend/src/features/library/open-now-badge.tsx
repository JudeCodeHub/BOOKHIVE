"use client";

import { Clock } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { libraryToday, useHoursFormat, useLibraryHours } from "@/features/library/hours";
import { cn } from "@/lib/utils";

/**
 * "Open now · closes at 5:00 PM" / "Closed · opens tomorrow at 8:00 AM" (checklist step 205).
 * The most common question people ask a library, answered on every page (footer) and on the
 * visit page. Shows nothing until the hours are known, rather than a wrong guess.
 */
export function OpenNowBadge({ className }: { className?: string }) {
  const t = useTranslations("Library");
  const locale = useLocale();
  const { time, when } = useHoursFormat();
  const hours = useLibraryHours();
  if (!hours.data) return null;

  const h = hours.data;
  const holidayName = h.todayHoliday
    ? locale === "si"
      ? h.todayHoliday.nameSi
      : locale === "ta"
        ? h.todayHoliday.nameTa
        : h.todayHoliday.nameEn
    : null;

  return (
    <p
      role="status"
      className={cn("inline-flex flex-wrap items-center gap-x-2 gap-y-1 text-sm", className)}
    >
      <span
        className={cn(
          "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 font-medium",
          h.openNow
            ? "bg-available/20 text-available-foreground"
            : "bg-overdue/15 text-overdue-foreground",
        )}
      >
        <Clock aria-hidden className="size-3.5" />
        {h.openNow ? t("openNow") : t("closed")}
      </span>
      <span>
        {h.openNow && h.closesAt && t("closesAt", { time: time(h.closesAt) })}
        {!h.openNow && holidayName && `${t("holidayToday", { name: holidayName })} · `}
        {!h.openNow && h.nextOpening && t("opensAt", { when: when(h.nextOpening, libraryToday()) })}
      </span>
    </p>
  );
}
