"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { BookCard } from "@/features/catalog/book-card";
import { Link } from "@/i18n/navigation";
import { api, type Schema } from "@/lib/api";

type BookSummary = Schema<"BookSummary">;

/**
 * A row of books on the home page (checklist step 202): "New arrivals", "Popular". Loaded in the
 * browser, so the page itself stays static and fast and availability is always current. Hides
 * itself when there are no books (e.g. "Popular" before anything has been borrowed).
 */
export function BookShelf({
  id,
  title,
  subtitle,
  endpoint,
  seeAllHref,
}: {
  id: string;
  title: string;
  subtitle: string;
  endpoint: "/books/new-arrivals" | "/books/popular";
  seeAllHref: string;
}) {
  const t = useTranslations("Home");
  const query = useQuery({
    queryKey: ["home", endpoint],
    queryFn: () => api.get<BookSummary[]>(endpoint, { query: { limit: 12 } }),
    staleTime: 5 * 60_000,
  });

  if (query.isError || (query.isSuccess && query.data.length === 0)) return null;

  return (
    <section aria-labelledby={id} className="mx-auto w-full max-w-6xl px-4 py-10">
      <div className="mb-4 flex items-end justify-between gap-4">
        <div>
          <h2 id={id} className="text-2xl font-semibold sm:text-3xl">
            {title}
          </h2>
          <p className="mt-1 text-muted-foreground">{subtitle}</p>
        </div>
        <Link
          href={seeAllHref}
          className="inline-flex shrink-0 items-center gap-1 text-sm font-medium underline-offset-4 hover:underline"
        >
          {t("seeAll")}
          <ArrowRight aria-hidden className="size-4" />
        </Link>
      </div>
      {/* A row that scrolls sideways on phones and wraps into a grid on wider screens */}
      <ul className="-mx-4 flex snap-x gap-3 overflow-x-auto px-4 pb-2 sm:mx-0 sm:grid sm:grid-cols-3 sm:overflow-visible sm:px-0 lg:grid-cols-6">
        {query.isPending
          ? Array.from({ length: 6 }, (_, i) => (
              <li
                key={i}
                aria-hidden
                className="w-40 shrink-0 animate-pulse rounded-lg border p-3 sm:w-auto"
              >
                <div className="aspect-[2/3] rounded bg-muted" />
                <div className="mt-2 h-4 rounded bg-muted" />
              </li>
            ))
          : query.data.map((book) => (
              <li key={book.id} className="grid w-40 shrink-0 snap-start sm:w-auto">
                <BookCard book={book} />
              </li>
            ))}
      </ul>
    </section>
  );
}
