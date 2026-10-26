"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Pagination } from "@/components/layout/pagination";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { BookCard } from "@/features/catalog/book-card";
import { usePathname, useRouter } from "@/i18n/navigation";
import { api, type Schema } from "@/lib/api";

type SearchResponse = Schema<"SearchResponse">;
type Sort = "NEWEST" | "TITLE" | "YEAR";

const PAGE_SIZE = 24;
const SORTS: Sort[] = ["NEWEST", "TITLE", "YEAR"];

/**
 * The books of a category or an author (checklist steps 203-204), loaded in the browser with live
 * availability. Page and sort are in the URL (?page=&sort=), the filter comes from the page.
 */
export function BookResults({ filter }: { filter: { category: string } | { author: string } }) {
  const t = useTranslations("BookResults");
  const tSearch = useTranslations("Search");
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const page = Math.max(0, Number(params.get("page") ?? 0) || 0);
  const sortParam = params.get("sort") as Sort | null;
  const sort: Sort = sortParam && SORTS.includes(sortParam) ? sortParam : "TITLE";

  const query = useQuery({
    queryKey: ["book-results", filter, sort, page],
    queryFn: () =>
      api.get<SearchResponse>("/search", {
        query: { ...filter, sort, page, size: PAGE_SIZE },
      }),
    placeholderData: keepPreviousData,
  });

  function go(next: { page?: number; sort?: Sort }) {
    const query = new URLSearchParams();
    const nextSort = next.sort ?? sort;
    const nextPage = next.sort ? 0 : (next.page ?? page);
    if (nextSort !== "TITLE") query.set("sort", nextSort);
    if (nextPage > 0) query.set("page", String(nextPage));
    const qs = query.toString();
    router.push(qs ? `${pathname}?${qs}` : pathname);
  }

  if (query.isPending) {
    return (
      <div className="flex justify-center py-12">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (query.isError) return <FormAlert>{errorMessage(query.error)}</FormAlert>;

  const results = query.data.results;
  return (
    <div className="grid gap-4" aria-busy={query.isFetching}>
      <div className="flex flex-wrap items-center gap-2">
        <p className="mr-auto text-sm text-muted-foreground" role="status">
          {t("count", { count: results.totalElements })}
        </p>
        <label className="flex items-center gap-2 text-sm">
          {tSearch("sort")}
          <select
            className="h-8 rounded-lg border bg-transparent px-2 text-sm"
            value={sort}
            onChange={(event) => go({ sort: event.target.value as Sort })}
          >
            {SORTS.map((value) => (
              <option key={value} value={value}>
                {tSearch(`sorts.${value}`)}
              </option>
            ))}
          </select>
        </label>
      </div>
      {results.totalElements === 0 ? (
        <p className="rounded-lg border p-8 text-center text-muted-foreground">{t("empty")}</p>
      ) : (
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {results.content.map((book) => (
            <li key={book.id} className="grid">
              <BookCard book={book} />
            </li>
          ))}
        </ul>
      )}
      <Pagination
        page={page}
        totalPages={results.totalPages}
        onChange={(next) => go({ page: next })}
      />
    </div>
  );
}
