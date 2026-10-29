"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Pin } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { DateTile } from "@/features/events/date-tile";
import { PlacesBadge } from "@/features/events/event-card";
import type { LibraryEvent } from "@/features/events/library-dates";
import { Link } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";

type NewsSummary = Schema<"NewsSummary">;

const HOW_MANY = 3;

/**
 * "What's on" on the home page (checklist step 352): the next few events and the latest news.
 * Loaded in the browser like the book rows, so the home page stays static and places left are
 * current. A column with nothing in it is left out; with neither, the whole section is.
 */
export function WhatsOn() {
  const t = useTranslations("Home");
  const events = useQuery({
    queryKey: ["home", "events"],
    queryFn: () =>
      api.get<PageResponse<LibraryEvent>>("/events", { query: { page: 0, size: HOW_MANY } }),
    staleTime: 60_000,
  });
  const news = useQuery({
    queryKey: ["home", "news"],
    queryFn: () =>
      api.get<PageResponse<NewsSummary>>("/news", { query: { page: 0, size: HOW_MANY } }),
    staleTime: 5 * 60_000,
  });

  const eventList = events.data?.content ?? [];
  const newsList = news.data?.content ?? [];
  if (eventList.length === 0 && newsList.length === 0) return null;

  return (
    <section aria-labelledby="whats-on" className="mx-auto w-full max-w-6xl px-4 py-10">
      <h2 id="whats-on" className="mb-6 text-2xl font-semibold sm:text-3xl">
        {t("whatsOn")}
      </h2>
      <div className="grid gap-8 md:grid-cols-2">
        {eventList.length > 0 && (
          <Column title={t("comingUp")} seeAll={t("allEvents")} href="/events">
            {eventList.map((event) => (
              <li key={event.id}>
                <EventItem event={event} />
              </li>
            ))}
          </Column>
        )}
        {newsList.length > 0 && (
          <Column title={t("latestNews")} seeAll={t("allNews")} href="/news">
            {newsList.map((post) => (
              <li key={post.id}>
                <NewsItem post={post} />
              </li>
            ))}
          </Column>
        )}
      </div>
    </section>
  );
}

function Column({
  title,
  seeAll,
  href,
  children,
}: {
  title: string;
  seeAll: string;
  href: "/events" | "/news";
  children: React.ReactNode;
}) {
  return (
    <div className="grid content-start gap-3">
      <div className="flex items-end justify-between gap-4">
        <h3 className="text-lg font-semibold">{title}</h3>
        <Link
          href={href}
          className="inline-flex shrink-0 items-center gap-1 text-sm font-medium underline-offset-4 hover:underline"
        >
          {seeAll}
          <ArrowRight aria-hidden className="size-4" />
        </Link>
      </div>
      <ul className="grid gap-3">{children}</ul>
    </div>
  );
}

/** Date tile, title, time and place, and whether there are places left. */
function EventItem({ event }: { event: LibraryEvent }) {
  const format = useFormatter();
  const start = new Date(event.startsAt);
  return (
    <article className="group relative flex gap-3 rounded-lg border bg-card p-3 transition-shadow hover:shadow-md">
      <DateTile date={start} className="w-14 shrink-0" />
      <div className="grid min-w-0 content-start gap-1">
        <h4 className="line-clamp-2 font-semibold">
          <Link
            href={`/events/${event.id}`}
            className="group-has-focus-visible:ring-3 group-has-focus-visible:ring-ring/50 after:absolute after:inset-0 after:rounded-lg focus-visible:outline-none"
          >
            {event.title}
          </Link>
        </h4>
        <p className="text-sm text-muted-foreground">
          {format.dateTime(start, { weekday: "short", hour: "numeric", minute: "2-digit" })} ·{" "}
          {event.venue}
        </p>
        <div>
          <PlacesBadge event={event} />
        </div>
      </div>
    </article>
  );
}

/** Date, title and the start of the summary. */
function NewsItem({ post }: { post: NewsSummary }) {
  const t = useTranslations("News");
  const format = useFormatter();
  return (
    <article className="group relative grid gap-1 rounded-lg border bg-card p-3 transition-shadow hover:shadow-md">
      <p className="flex items-center gap-2 text-xs text-muted-foreground">
        {post.pinned && (
          <span className="inline-flex items-center gap-1 font-medium text-primary">
            <Pin aria-hidden className="size-3" />
            {t("pinned")}
          </span>
        )}
        <time dateTime={post.publishedAt}>
          {format.dateTime(new Date(post.publishedAt), { dateStyle: "medium" })}
        </time>
      </p>
      <h4 className="line-clamp-2 font-semibold">
        <Link
          href={`/news/${post.id}`}
          className="group-has-focus-visible:ring-3 group-has-focus-visible:ring-ring/50 after:absolute after:inset-0 after:rounded-lg focus-visible:outline-none"
        >
          {post.title}
        </Link>
      </h4>
      {post.summary && <p className="line-clamp-2 text-sm text-muted-foreground">{post.summary}</p>}
    </article>
  );
}
