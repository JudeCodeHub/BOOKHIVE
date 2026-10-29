"use client";

import { CalendarDays, Clock, MapPin } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { Link } from "@/i18n/navigation";
import { cn } from "@/lib/utils";
import { DateTile } from "./date-tile";
import { libraryDay, plainText, posterUrl, type LibraryEvent } from "./library-dates";

/**
 * An event in a list (checklist step 341): poster, date, time, place, who it's for and whether
 * there are places left. The whole card links to the event page.
 */
export function EventCard({ event, className }: { event: LibraryEvent; className?: string }) {
  const t = useTranslations("Events");
  const format = useFormatter();
  const poster = posterUrl(event);
  const start = new Date(event.startsAt);
  const end = new Date(event.endsAt);
  const sameDay = libraryDay(start) === libraryDay(end);
  const teaser = plainText(event.description);

  return (
    <article
      className={cn(
        "group relative grid overflow-hidden rounded-lg border bg-card transition-shadow hover:shadow-md sm:grid-cols-[12rem_1fr]",
        className,
      )}
    >
      {poster ? (
        // Posters are already resized JPEGs from the API, cached for a month
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={poster}
          alt=""
          loading="lazy"
          className="aspect-video w-full object-cover sm:aspect-auto sm:h-full"
        />
      ) : (
        <DateTile date={start} size="large" />
      )}
      <div className="grid content-start gap-2 p-4">
        <h3 className="font-heading text-lg font-semibold">
          <Link
            href={`/events/${event.id}`}
            className="group-has-focus-visible:ring-3 group-has-focus-visible:ring-ring/50 after:absolute after:inset-0 after:rounded-lg focus-visible:outline-none"
          >
            {event.title}
          </Link>
        </h3>
        <ul className="grid gap-1 text-sm text-muted-foreground">
          <li className="flex items-center gap-2">
            <CalendarDays aria-hidden className="size-4 shrink-0" />
            {sameDay
              ? format.dateTime(start, { weekday: "long", day: "numeric", month: "long" })
              : format.dateTimeRange(start, end, { day: "numeric", month: "long" })}
          </li>
          <li className="flex items-center gap-2">
            <Clock aria-hidden className="size-4 shrink-0" />
            {format.dateTimeRange(start, end, { hour: "numeric", minute: "2-digit" })}
          </li>
          <li className="flex items-center gap-2">
            <MapPin aria-hidden className="size-4 shrink-0" />
            {event.venue}
          </li>
        </ul>
        {teaser && <p className="line-clamp-2 text-sm">{teaser}</p>}
        <div className="flex flex-wrap gap-2">
          {event.audience !== "ALL" && (
            <Badge variant="outline">{t(`audience.${event.audience}`)}</Badge>
          )}
          <PlacesBadge event={event} />
        </div>
      </div>
    </article>
  );
}

/** Cancelled, no sign-up needed, places left, full (waitlist) or sign-up closed. */
export function PlacesBadge({ event }: { event: LibraryEvent }) {
  const t = useTranslations("Events");
  if (event.status === "CANCELLED") return <Badge variant="destructive">{t("cancelled")}</Badge>;
  if (!event.registrationRequired) return <Badge variant="secondary">{t("justComeAlong")}</Badge>;
  if (!event.openForRegistration) return <Badge variant="outline">{t("signUpClosed")}</Badge>;
  if (event.placesLeft == null) return <Badge variant="available">{t("signUpOpen")}</Badge>;
  if (event.placesLeft > 0) {
    return <Badge variant="available">{t("placesLeft", { count: event.placesLeft })}</Badge>;
  }
  return <Badge variant="on-loan">{t("fullWaitlist")}</Badge>;
}
