"use client";

import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { BookOpen, FileUp, Plus, Search } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Pager } from "@/features/staff/pager";
import { Link, usePathname, useRouter } from "@/i18n/navigation";
import { coverUrl } from "@/features/catalog/cover";
import { api, type PageResponse, type Schema } from "@/lib/api";

type BookSummary = Schema<"BookSummary">;
type CategoryNode = Schema<"CategoryTreeResponse">;

const FORMATS = ["BOOK", "MAGAZINE", "NEWSPAPER", "DVD", "AUDIOBOOK"] as const;
const SORTS = ["TITLE", "NEWEST", "YEAR"] as const;
const LANGUAGES = ["si", "ta", "en"] as const;

/** Categories as a flat list with depth, for an indented <select>. */
function flatten(nodes: CategoryNode[], depth = 0): { node: CategoryNode; depth: number }[] {
  return nodes.flatMap((node) => [{ node, depth }, ...flatten(node.children, depth + 1)]);
}

/**
 * Staff catalog list (checklist step 172): find a book by title or ISBN, filter by language,
 * format and category, sort, page. Filters live in the URL, so a filtered list can be bookmarked.
 */
export function CatalogList() {
  const t = useTranslations("StaffCatalog");
  const tCommon = useTranslations("Common");
  const locale = useLocale();
  const errorMessage = useAuthErrorMessage();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const q = params.get("q") ?? "";
  const language = params.get("language") ?? "";
  const format = params.get("format") ?? "";
  const category = params.get("category") ?? "";
  const sort = (params.get("sort") ?? "TITLE") as (typeof SORTS)[number];
  const page = Number(params.get("page") ?? 0);
  const [text, setText] = useState(q);

  /** Changes some filters and goes back to page 1 (except when changing the page itself). */
  function update(changes: Record<string, string | number>) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === "" || value === 0) next.delete(key);
      else next.set(key, String(value));
    }
    if (!("page" in changes)) next.delete("page");
    const query = next.toString();
    router.replace(query ? `${pathname}?${query}` : pathname);
  }

  const books = useQuery({
    queryKey: ["staff", "catalog", q, language, format, category, sort, page],
    queryFn: () =>
      api.get<PageResponse<BookSummary>>("/books", {
        query: { q, language, format, category, sort, page, size: 20 },
      }),
    placeholderData: keepPreviousData,
  });
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: () => api.get<CategoryNode[]>("/categories"),
    staleTime: 5 * 60_000,
  });

  const categoryName = (node: CategoryNode) =>
    locale === "si" ? node.nameSi : locale === "ta" ? node.nameTa : node.nameEn;
  const selectClass = "h-8 rounded-lg border bg-transparent px-2.5 text-sm";

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap gap-2">
        <Button asChild>
          <Link href="/staff/catalog/new">
            <Plus aria-hidden />
            {t("addBook")}
          </Link>
        </Button>
        <Button asChild variant="secondary">
          <Link href="/staff/catalog/import">
            <FileUp aria-hidden />
            {t("import")}
          </Link>
        </Button>
      </div>

      <form
        role="search"
        className="flex max-w-xl gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          update({ q: text.trim() });
        }}
      >
        <label htmlFor="catalog-search" className="sr-only">
          {t("searchLabel")}
        </label>
        <Input
          id="catalog-search"
          type="search"
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder={t("searchPlaceholder")}
        />
        <Button type="submit">
          <Search aria-hidden />
          {t("search")}
        </Button>
      </form>

      <div className="flex flex-wrap items-center gap-2">
        <select
          aria-label={t("language")}
          className={selectClass}
          value={language}
          onChange={(event) => update({ language: event.target.value })}
        >
          <option value="">{t("anyLanguage")}</option>
          {LANGUAGES.map((code) => (
            <option key={code} value={code}>
              {t(`languages.${code}`)}
            </option>
          ))}
        </select>
        <select
          aria-label={t("format")}
          className={selectClass}
          value={format}
          onChange={(event) => update({ format: event.target.value })}
        >
          <option value="">{t("anyFormat")}</option>
          {FORMATS.map((value) => (
            <option key={value} value={value}>
              {t(`formats.${value}`)}
            </option>
          ))}
        </select>
        <select
          aria-label={t("category")}
          className={`${selectClass} max-w-64`}
          value={category}
          onChange={(event) => update({ category: event.target.value })}
        >
          <option value="">{t("anyCategory")}</option>
          {flatten(categories.data ?? []).map(({ node, depth }) => (
            <option key={node.id} value={node.id}>
              {"  ".repeat(depth)}
              {node.ddcCode ? `${node.ddcCode} ` : ""}
              {categoryName(node)}
            </option>
          ))}
        </select>
        <select
          aria-label={t("sort")}
          className={selectClass}
          value={sort}
          onChange={(event) =>
            update({ sort: event.target.value === "TITLE" ? "" : event.target.value })
          }
        >
          {SORTS.map((value) => (
            <option key={value} value={value}>
              {t(`sorts.${value}`)}
            </option>
          ))}
        </select>
        {(q || language || format || category) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setText("");
              router.replace(pathname);
            }}
          >
            {t("clear")}
          </Button>
        )}
      </div>

      {books.isPending && (
        <div className="flex justify-center py-8">
          <HexLoader label={tCommon("loading")} />
        </div>
      )}
      {books.isError && <FormAlert>{errorMessage(books.error)}</FormAlert>}
      {books.data && (
        <>
          <p className="text-sm text-muted-foreground" role="status">
            {t("results", { count: books.data.totalElements })}
          </p>
          {books.data.totalElements === 0 ? (
            <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("empty")}</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-14">
                    <span className="sr-only">{t("cover")}</span>
                  </TableHead>
                  <TableHead>{t("book")}</TableHead>
                  <TableHead>{t("language")}</TableHead>
                  <TableHead>{t("format")}</TableHead>
                  <TableHead>{t("year")}</TableHead>
                  <TableHead>{t("callNumber")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {books.data.content.map((book) => {
                  const cover = coverUrl(book.id, book.coverVersion);
                  return (
                    <TableRow key={book.id}>
                      <TableCell>
                        {cover ? (
                          // A small public cover served by our API; next/image would need the
                          // API host whitelisted for no real gain at this size
                          // eslint-disable-next-line @next/next/no-img-element
                          <img
                            src={cover}
                            alt=""
                            loading="lazy"
                            className="h-14 w-10 rounded object-cover"
                          />
                        ) : (
                          <span className="flex h-14 w-10 items-center justify-center rounded bg-muted">
                            <BookOpen
                              aria-label={t("noCover")}
                              className="size-4 text-muted-foreground"
                            />
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        <Link
                          href={`/staff/catalog/${book.id}`}
                          className="font-medium underline-offset-4 hover:underline"
                        >
                          {book.title}
                        </Link>
                        {book.subtitle && (
                          <span className="block text-xs text-muted-foreground">
                            {book.subtitle}
                          </span>
                        )}
                        {book.contributors.length > 0 && (
                          <span className="block text-xs text-muted-foreground">
                            {book.contributors.join(", ")}
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        {(LANGUAGES as readonly string[]).includes(book.language)
                          ? t(`languages.${book.language as (typeof LANGUAGES)[number]}`)
                          : book.language}
                      </TableCell>
                      <TableCell>{t(`formats.${book.format}`)}</TableCell>
                      <TableCell>{book.publishYear ?? "–"}</TableCell>
                      <TableCell className="font-mono text-xs">{book.callNumber ?? "–"}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
          <Pager
            page={page}
            totalPages={books.data.totalPages}
            onChange={(next) => update({ page: next })}
          />
        </>
      )}
    </div>
  );
}
