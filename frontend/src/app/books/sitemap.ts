import type { MetadataRoute } from "next";
import { connection } from "next/server";
import { routing } from "@/i18n/routing";
import { API_BASE_URL, type Schema } from "@/lib/api";
import { BOOKS_PER_SITEMAP, BOOK_SITEMAPS, languageAlternates, url } from "@/lib/seo/site";

type Entry = Schema<"SitemapEntrySummary">;

/**
 * Book sitemaps (checklist step 209): /books/sitemap/0.xml, 1.xml... with 40,000 books each
 * (search engines accept 50,000 URLs per file). Each book is listed once, with its versions in
 * the other languages. Files past the end of the catalog are simply empty.
 */
export async function generateSitemaps() {
  return Array.from({ length: BOOK_SITEMAPS }, (_, id) => ({ id }));
}

export default async function sitemap(props: {
  id: Promise<string>;
}): Promise<MetadataRoute.Sitemap> {
  // Request time, not build time: the catalog changes, and the build may have no API
  await connection();
  const page = Number(await props.id);
  let books: Entry[] = [];
  try {
    const response = await fetch(
      `${API_BASE_URL}/sitemap/books?page=${page}&size=${BOOKS_PER_SITEMAP}`,
      { headers: { Accept: "application/json" } },
    );
    if (response.ok) books = (await response.json()) as Entry[];
  } catch {
    // API unreachable: an empty sitemap is better than an error page for crawlers
  }
  return books.map((book) => ({
    url: url(routing.defaultLocale, `/books/${book.id}`),
    lastModified: book.updatedAt,
    alternates: { languages: languageAlternates(`/books/${book.id}`) },
    changeFrequency: "weekly",
    priority: 0.8,
  }));
}
