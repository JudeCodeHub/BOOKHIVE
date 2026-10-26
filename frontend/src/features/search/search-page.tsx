"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { BookX, LayoutGrid, Lightbulb, List, SlidersHorizontal } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Pagination } from "@/components/layout/pagination";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { BookCard } from "@/features/catalog/book-card";
import { SearchFilters } from "@/features/search/search-filters";
import { readSearch, writeSearch, type SearchState } from "@/features/search/search-params";
import { Link, usePathname, useRouter } from "@/i18n/navigation";
import { api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type SearchResponse = Schema<"SearchResponse">;
type CategoryNode = Schema<"CategoryTreeResponse">;

const PAGE_SIZE = 24;

/**
 * The public search page (checklist steps 197-200). Everything lives in the URL (?q=&language=
 * &page=...), so a search can be shared or bookmarked and the back button undoes a filter.
 */
export function SearchPage() {
  const t = useTranslations("Search");
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const router = useRouter();
  const pathname = usePathname();
  const state = readSearch(useSearchParams());
  const [drawerOpen, setDrawerOpen] = useState(false);

  /** Changes some values; any change except paging and view goes back to page 1. */
  function update(changes: Partial<SearchState>) {
    const keepPage = Object.keys(changes).every((k) => k === "page" || k === "view");
    const next = { ...state, ...changes, page: keepPage ? (changes.page ?? state.page) : 0 };
    const query = writeSearch(next);
    // push, not replace: the back button steps back through searches and filters
    router.push(query ? `${pathname}?${query}` : pathname, { scroll: "page" in changes });
  }

  const results = useQuery({
    queryKey: ["search", writeSearch({ ...state, view: "grid" })],
    queryFn: () =>
      api.get<SearchResponse>("/search", {
        query: {
          q: state.q,
          language: state.language || undefined,
          format: state.format || undefined,
          category: state.category || undefined,
          available: state.available || undefined,
          yearFrom: state.yearFrom || undefined,
          yearTo: state.yearTo || undefined,
          sort: state.sort,
          page: state.page,
          size: PAGE_SIZE,
        },
      }),
    placeholderData: keepPreviousData,
  });
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: () => api.get<CategoryNode[]>("/categories"),
    staleTime: 10 * 60_000,
  });

  const data = results.data;
  const filters = (
    <SearchFilters
      // Fresh year inputs when the URL changes (e.g. "Clear all", back button)
      key={`${state.yearFrom}-${state.yearTo}`}
      state={state}
      facets={data?.facets}
      categories={categories.data ?? []}
      onChange={update}
    />
  );

  return (
    <div className="mx-auto grid w-full max-w-6xl gap-6 px-4 py-6 md:grid-cols-[15rem_1fr]">
      <h1 className="font-heading text-2xl font-semibold md:col-span-2">
        {state.q ? t("resultsFor", { q: state.q }) : t("allBooks")}
      </h1>

      {/* Sidebar on wide screens */}
      <aside aria-label={t("filters")} className="hidden md:block">
        {filters}
      </aside>

      <section className="grid content-start gap-4" aria-busy={results.isFetching}>
        <div className="flex flex-wrap items-center gap-2">
          <p className="mr-auto text-sm text-muted-foreground" role="status">
            {data && t("count", { count: data.results.totalElements })}
          </p>
          <Button
            variant="secondary"
            size="sm"
            className="md:hidden"
            onClick={() => setDrawerOpen(true)}
          >
            <SlidersHorizontal aria-hidden />
            {t("showFilters")}
          </Button>
          <label className="flex items-center gap-2 text-sm">
            <span className="sr-only sm:not-sr-only">{t("sort")}</span>
            <select
              className="h-8 rounded-lg border bg-transparent px-2 text-sm"
              value={state.sort}
              onChange={(event) => update({ sort: event.target.value as SearchState["sort"] })}
            >
              {(["RELEVANCE", "NEWEST", "TITLE", "YEAR"] as const).map((sort) => (
                <option key={sort} value={sort}>
                  {t(`sorts.${sort}`)}
                </option>
              ))}
            </select>
          </label>
          <div role="group" aria-label={t("view")} className="flex rounded-lg border p-0.5">
            {(["grid", "list"] as const).map((view) => (
              <Button
                key={view}
                variant={state.view === view ? "secondary" : "ghost"}
                size="icon"
                className="size-7"
                aria-label={t(view)}
                aria-pressed={state.view === view}
                onClick={() => update({ view })}
              >
                {view === "grid" ? <LayoutGrid /> : <List />}
              </Button>
            ))}
          </div>
        </div>

        {results.isPending && (
          <div className="flex justify-center py-12">
            <HexLoader label={tCommon("loading")} />
          </div>
        )}
        {results.isError && <FormAlert>{errorMessage(results.error)}</FormAlert>}
        {data && data.results.totalElements === 0 && (
          <EmptyResults
            q={state.q}
            hasFilters={
              !!(
                state.language ||
                state.format ||
                state.category ||
                state.available ||
                state.yearFrom ||
                state.yearTo
              )
            }
            onClearFilters={() =>
              update({
                language: "",
                format: "",
                category: "",
                available: false,
                yearFrom: "",
                yearTo: "",
              })
            }
          />
        )}
        {data && data.results.content.length > 0 && (
          <ul
            className={cn(
              "grid gap-3 transition-opacity",
              state.view === "grid"
                ? "grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
                : "grid-cols-1",
              results.isPlaceholderData && "opacity-60",
            )}
          >
            {data.results.content.map((book) => (
              <li key={book.id} className="grid">
                <BookCard book={book} layout={state.view} />
              </li>
            ))}
          </ul>
        )}
        {data && (
          <Pagination
            page={state.page}
            totalPages={data.results.totalPages}
            onChange={(page) => update({ page })}
          />
        )}
      </section>

      {/* Drawer on phones: the same filters, results update behind it */}
      <Dialog open={drawerOpen} onOpenChange={setDrawerOpen}>
        <DialogContent className="top-0 right-0 left-auto h-full max-h-none w-[min(22rem,90vw)] translate-x-0 translate-y-0 overflow-y-auto rounded-none sm:max-w-none">
          <DialogHeader>
            <DialogTitle>{t("filters")}</DialogTitle>
          </DialogHeader>
          {filters}
          <DialogFooter>
            <Button onClick={() => setDrawerOpen(false)}>
              {t("closeFilters", { count: data?.results.totalElements ?? 0 })}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/**
 * Nothing found (checklist step 201): help the reader on, instead of a dead end. Fewer filters,
 * or ask the library to buy the book: the suggestion form opens with the title filled in.
 */
function EmptyResults({
  q,
  hasFilters,
  onClearFilters,
}: {
  q: string;
  hasFilters: boolean;
  onClearFilters: () => void;
}) {
  const t = useTranslations("Search");
  return (
    <div className="grid justify-items-center gap-3 rounded-lg border p-8 text-center">
      <BookX aria-hidden className="size-10 text-muted-foreground" />
      <p className="font-medium">{q ? t("emptyTitle", { q }) : t("emptyNoQuery")}</p>
      <p className="text-sm text-muted-foreground">{t("tryLess")}</p>
      <div className="flex flex-wrap justify-center gap-2">
        {hasFilters && (
          <Button variant="secondary" onClick={onClearFilters}>
            {t("clearFilters")}
          </Button>
        )}
        {q && (
          <Button asChild>
            {/* Visitors are asked to log in first, then land back on the form */}
            <Link href={`/account/suggestions?title=${encodeURIComponent(q)}`}>
              <Lightbulb aria-hidden />
              {t("suggestPurchase")}
            </Link>
          </Button>
        )}
      </div>
    </div>
  );
}
