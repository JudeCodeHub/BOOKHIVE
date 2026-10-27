"use client";

import { useQuery } from "@tanstack/react-query";
import { BookmarkCheck } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Link } from "@/i18n/navigation";
import { api, type Schema } from "@/lib/api";

type HoldSummary = Schema<"HoldSummary">;

/**
 * "Ready for pickup" on the member dashboard (checklist step 263): the reserved books waiting at
 * the desk, soonest deadline first, so nobody misses their pickup window. Shows nothing when
 * there are none (or the person isn't a member yet).
 */
export function ReadyForPickupBanner() {
  const t = useTranslations("ReadyBanner");
  const format = useFormatter();
  // Same query as My holds: one fetch, and cancelling there updates this too
  const holds = useQuery({
    queryKey: ["me", "holds"],
    queryFn: () => api.get<HoldSummary[]>("/me/holds"),
    retry: false,
  });

  const ready = (holds.data ?? [])
    .filter((hold) => hold.status === "READY" && hold.expiresAt)
    .sort((a, b) => (a.expiresAt ?? "").localeCompare(b.expiresAt ?? ""));
  if (ready.length === 0) return null;

  const lastDay = (iso: string) =>
    format.dateTime(new Date(`${iso}T00:00:00`), {
      weekday: "long",
      day: "numeric",
      month: "long",
    });

  return (
    <section
      aria-labelledby="ready-banner-title"
      className="grid gap-3 rounded-xl border border-primary bg-primary/10 p-4 sm:p-5"
    >
      <h2 id="ready-banner-title" className="flex items-center gap-2 text-lg font-semibold">
        <BookmarkCheck aria-hidden className="size-5 text-primary" />
        {t("title", { count: ready.length })}
      </h2>
      <ul className="grid gap-1.5">
        {ready.map((hold) => (
          <li key={hold.holdId} className="text-sm">
            <Link href={`/books/${hold.bookId}`} className="font-medium hover:underline">
              {hold.title}
            </Link>
            {" · "}
            {t("collectBy", { date: lastDay(hold.expiresAt!) })}
          </li>
        ))}
      </ul>
      <p className="text-sm text-muted-foreground">{t("hint")}</p>
      <div>
        <Button asChild size="sm" variant="outline">
          <Link href="/account/holds">{t("seeHolds")}</Link>
        </Button>
      </div>
    </section>
  );
}
