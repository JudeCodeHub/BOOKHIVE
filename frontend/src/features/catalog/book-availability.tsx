"use client";

import { useQuery } from "@tanstack/react-query";
import { CircleCheck, CircleSlash, MapPin } from "lucide-react";
import { useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type Availability = Schema<"AvailabilityResponse">;

/**
 * "2 of 3 available to borrow · Find it at: Sinhala fiction, shelf 3" (SRCH-4). Loaded in the
 * browser on every visit, because it changes with every checkout and return; the rest of the
 * book page is cached on the server.
 */
export function BookAvailability({ bookId, callNumber }: { bookId: string; callNumber?: string }) {
  const t = useTranslations("BookPage");
  const query = useQuery({
    queryKey: ["availability", bookId],
    queryFn: () => api.get<Availability>(`/books/${bookId}/availability`),
    // Fresh enough for someone deciding whether to walk to the library
    refetchInterval: 60_000,
  });

  if (query.isPending) return <HexLoader label={t("loading")} />;
  if (query.isError) return <p className="text-sm text-muted-foreground">{t("unavailable")}</p>;

  const a = query.data;
  const lendable = a.total - a.referenceOnly;
  const canBorrow = a.availableToBorrow > 0;

  return (
    <div className="grid gap-2" role="status">
      <p
        className={cn(
          "flex items-center gap-2 font-medium",
          canBorrow ? "text-available-foreground" : "text-on-loan-foreground",
        )}
      >
        {canBorrow ? (
          <CircleCheck aria-hidden className="size-5" />
        ) : (
          <CircleSlash aria-hidden className="size-5" />
        )}
        {a.total === 0
          ? t("notInStock")
          : canBorrow
            ? t("availableToBorrow", { available: a.availableToBorrow, total: lendable })
            : t("noneAvailable")}
      </p>
      <ul className="grid gap-1 text-sm text-muted-foreground">
        {a.referenceOnly > 0 && <li>{t("referenceOnly", { count: a.referenceOnly })}</li>}
        {a.onLoan > 0 && <li>{t("onLoan", { count: a.onLoan })}</li>}
        {a.onHoldShelf > 0 && <li>{t("onHoldShelf", { count: a.onHoldShelf })}</li>}
        {a.holdsWaiting > 0 && <li>{t("holdsWaiting", { count: a.holdsWaiting })}</li>}
      </ul>
      {a.available > 0 && (
        <p className="flex items-start gap-2 text-sm">
          <MapPin aria-hidden className="mt-0.5 size-4 shrink-0 text-primary" />
          <span>
            {a.shelves.length > 0
              ? t("shelves", { shelves: a.shelves.join(" · ") })
              : t("askAtDesk")}
            {callNumber && <span className="ml-2 font-mono">{callNumber}</span>}
          </span>
        </p>
      )}
    </div>
  );
}
