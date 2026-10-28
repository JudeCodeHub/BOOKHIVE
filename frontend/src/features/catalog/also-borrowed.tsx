"use client";

import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { BookCard } from "@/features/catalog/book-card";
import { api, type Schema } from "@/lib/api";

type BookSummary = Schema<"BookSummary">;

/**
 * "Readers also borrowed" on the book page (checklist step 351): up to 8 books that at least two
 * readers of this book also borrowed (the API leaves out anything that could point to one
 * person, step 339). Shown only once there's something to show, so most books, which have no
 * suggestions yet, don't get an empty row or a flash of placeholders.
 */
export function AlsoBorrowed({ bookId }: { bookId: string }) {
  const t = useTranslations("BookPage");
  const query = useQuery({
    queryKey: ["also-borrowed", bookId],
    queryFn: () => api.get<BookSummary[]>(`/books/${bookId}/also-borrowed`),
    // The API caches the ranking for hours; no need to ask again while browsing
    staleTime: 30 * 60_000,
  });

  if (!query.data || query.data.length === 0) return null;

  return (
    <section aria-labelledby="also-borrowed" className="grid gap-3 border-t pt-6 md:col-span-2">
      <div>
        <h2 id="also-borrowed" className="text-xl font-semibold">
          {t("alsoBorrowed")}
        </h2>
        <p className="text-sm text-muted-foreground">{t("alsoBorrowedHint")}</p>
      </div>
      {/* Scrolls sideways on phones, a grid on wider screens */}
      <ul className="-mx-4 flex snap-x gap-3 overflow-x-auto px-4 pb-2 sm:mx-0 sm:grid sm:grid-cols-4 sm:overflow-visible sm:px-0">
        {query.data.map((book) => (
          <li key={book.id} className="grid w-40 shrink-0 snap-start sm:w-auto">
            <BookCard book={book} />
          </li>
        ))}
      </ul>
    </section>
  );
}
