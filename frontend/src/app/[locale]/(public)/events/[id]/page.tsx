import { Suspense } from "react";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { CalendarDays, ChevronLeft, Clock, MapPin, Users } from "lucide-react";
import { getFormatter, getTranslations } from "next-intl/server";
import { RichText } from "@/components/common/rich-text";
import { getPublicEvent } from "@/features/events/data";
import { EventSignUp } from "@/features/events/event-sign-up";
import {
  libraryDay,
  plainText,
  posterUrl,
  type LibraryEvent,
} from "@/features/events/library-dates";
import { Link } from "@/i18n/navigation";
import { routing } from "@/i18n/routing";

// Checked in development for instant navigation (next.config: manual-warning)
export const instant = true;

/**
 * Public event page (checklist step 342): rendered on the server (search engines, link
 * previews), cached for a few minutes; the sign-up box loads places left fresh in the browser.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/events/[id]">): Promise<Metadata> {
  const { locale, id } = await params;
  const event = await getPublicEvent(id);
  if (!event) notFound();

  const description = plainText(event.description).slice(0, 160) || event.venue;
  const poster = posterUrl(event);
  return {
    title: event.title,
    description,
    alternates: {
      canonical: `/${locale}/events/${id}`,
      languages: Object.fromEntries(routing.locales.map((l) => [l, `/${l}/events/${id}`])),
    },
    openGraph: {
      title: event.title,
      description,
      images: poster ? [{ url: poster, alt: event.title }] : undefined,
    },
    // A cancelled event stays reachable (links in emails) but shouldn't be found in search
    robots: event.status === "CANCELLED" ? { index: false, follow: true } : undefined,
  };
}

export default function EventPage(props: PageProps<"/[locale]/events/[id]">) {
  return (
    <Suspense fallback={<EventSkeleton />}>
      <EventDetails {...props} />
    </Suspense>
  );
}

async function EventDetails({ params }: PageProps<"/[locale]/events/[id]">) {
  const { id } = await params;
  const event = await getPublicEvent(id);
  if (!event) notFound();

  const t = await getTranslations("EventPage");
  const tEvents = await getTranslations("Events");
  const format = await getFormatter();
  const start = new Date(event.startsAt);
  const end = new Date(event.endsAt);
  const sameDay = libraryDay(start) === libraryDay(end);
  const poster = posterUrl(event);

  return (
    <article className="mx-auto grid w-full max-w-5xl gap-6 px-4 py-8">
      <script
        type="application/ld+json"
        // Structured data for search engines; "<" escaped so the JSON can't close the tag
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(eventJsonLd(event)).replace(/</g, "\\u003c"),
        }}
      />
      <Link
        href="/events"
        className="flex w-fit items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft aria-hidden className="size-4" />
        {t("allEvents")}
      </Link>

      {event.status === "CANCELLED" && (
        <div
          role="status"
          className="rounded-lg border border-destructive/40 bg-destructive/10 p-4"
        >
          <p className="font-semibold text-destructive">{t("cancelledBanner")}</p>
          {event.cancelReason && <p className="mt-1 text-sm">{event.cancelReason}</p>}
        </div>
      )}

      <div className="grid gap-8 md:grid-cols-[1fr_18rem]">
        <div className="grid content-start gap-6">
          <h1 className="font-heading text-3xl font-semibold">{event.title}</h1>
          {poster && (
            // Posters are already resized JPEGs from the API, cached for a month
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={poster}
              alt=""
              fetchPriority="high"
              className="w-full rounded-lg border object-cover"
            />
          )}
          {event.description && (
            <section aria-labelledby="about" className="grid gap-2">
              <h2 id="about" className="text-xl font-semibold">
                {t("about")}
              </h2>
              <RichText html={event.description} />
            </section>
          )}
        </div>

        <aside className="grid content-start gap-4">
          <dl className="grid gap-3 rounded-lg border p-4 text-sm">
            <div className="flex items-start gap-2">
              <CalendarDays aria-hidden className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
              <div>
                <dt className="sr-only">{t("date")}</dt>
                <dd>
                  {sameDay
                    ? format.dateTime(start, {
                        weekday: "long",
                        day: "numeric",
                        month: "long",
                        year: "numeric",
                      })
                    : format.dateTimeRange(start, end, {
                        day: "numeric",
                        month: "long",
                        year: "numeric",
                      })}
                </dd>
              </div>
            </div>
            <div className="flex items-start gap-2">
              <Clock aria-hidden className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
              <div>
                <dt className="sr-only">{t("time")}</dt>
                <dd>{format.dateTimeRange(start, end, { hour: "numeric", minute: "2-digit" })}</dd>
              </div>
            </div>
            <div className="flex items-start gap-2">
              <MapPin aria-hidden className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
              <div>
                <dt className="sr-only">{t("venue")}</dt>
                <dd>{event.venue}</dd>
              </div>
            </div>
            <div className="flex items-start gap-2">
              <Users aria-hidden className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
              <div>
                <dt className="sr-only">{t("audience")}</dt>
                <dd>{tEvents(`audience.${event.audience}`)}</dd>
              </div>
            </div>
          </dl>
          <EventSignUp initial={event} />
        </aside>
      </div>
    </article>
  );
}

/** schema.org Event, so search engines can show the date and place. */
function eventJsonLd(event: LibraryEvent) {
  return {
    "@context": "https://schema.org",
    "@type": "Event",
    name: event.title,
    startDate: event.startsAt,
    endDate: event.endsAt,
    eventStatus:
      event.status === "CANCELLED"
        ? "https://schema.org/EventCancelled"
        : "https://schema.org/EventScheduled",
    eventAttendanceMode: "https://schema.org/OfflineEventAttendanceMode",
    location: { "@type": "Place", name: event.venue },
    description: plainText(event.description) || undefined,
    image: posterUrl(event) ?? undefined,
    organizer: { "@type": "Organization", name: "BookHive" },
  };
}

function EventSkeleton() {
  return (
    <div className="mx-auto grid w-full max-w-5xl animate-pulse gap-6 px-4 py-8" aria-hidden>
      <div className="h-4 w-24 rounded bg-muted" />
      <div className="grid gap-8 md:grid-cols-[1fr_18rem]">
        <div className="grid gap-4">
          <div className="h-9 w-2/3 rounded bg-muted" />
          <div className="aspect-video rounded-lg bg-muted" />
        </div>
        <div className="h-48 rounded-lg bg-muted" />
      </div>
    </div>
  );
}
