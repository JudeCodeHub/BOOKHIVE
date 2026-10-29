"use client";

import { useFormatter, useTranslations } from "next-intl";
import { cn } from "@/lib/utils";

/** Found of expected, as a bar and in words (the words carry it; the bar is a quick glance). */
export function Progress({
  found,
  expected,
  compact = false,
}: {
  found: number;
  expected: number;
  compact?: boolean;
}) {
  const t = useTranslations("Stocktake");
  const format = useFormatter();
  const share = expected > 0 ? Math.min(1, found / expected) : 0;
  return (
    <div className="grid gap-1">
      <div
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={expected}
        aria-valuenow={found}
        aria-label={t("progressLabel")}
        className={cn("overflow-hidden rounded-full bg-muted", compact ? "h-1.5" : "h-3")}
      >
        <div
          className="h-full rounded-full bg-primary transition-all"
          style={{ width: `${share * 100}%` }}
        />
      </div>
      <p className={cn("tabular-nums", compact ? "text-xs text-muted-foreground" : "text-sm")}>
        {t("progress", {
          found: format.number(found),
          expected: format.number(expected),
          percent: format.number(share, { style: "percent" }),
        })}
      </p>
    </div>
  );
}
