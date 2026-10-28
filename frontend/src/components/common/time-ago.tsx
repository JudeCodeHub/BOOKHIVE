"use client";

import { useFormatter, useNow } from "next-intl";

/**
 * "2 hours ago" in the site's language, kept up to date every minute.
 *
 * Only use it in content that appears in the browser (an opened menu, data loaded by a query):
 * it reads the current time, which Next.js doesn't allow while pre-rendering a page.
 */
export function TimeAgo({ date, className }: { date: string; className?: string }) {
  const format = useFormatter();
  const now = useNow({ updateInterval: 60_000 });
  return (
    <time dateTime={date} className={className}>
      {format.relativeTime(new Date(date), now)}
    </time>
  );
}
