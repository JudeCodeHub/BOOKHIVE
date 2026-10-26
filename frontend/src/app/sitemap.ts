import type { MetadataRoute } from "next";
import { connection } from "next/server";
import { routing } from "@/i18n/routing";
import { API_BASE_URL, type Schema } from "@/lib/api";
import { languageAlternates, url } from "@/lib/seo/site";

type CategoryNode = Schema<"CategoryTreeResponse">;
type Entry = Schema<"SitemapEntrySummary">;

/** Public pages that aren't tied to a book, author or category. */
const PAGES = ["/", "/search", "/visit", "/rules", "/register", "/privacy", "/terms"];

/**
 * Main sitemap (checklist step 209): the public pages, every category and every author, each
 * with its Sinhala/Tamil/English versions. Books have their own numbered sitemaps
 * (/books/sitemap/0.xml...). Built when requested, so building the site doesn't need the API.
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  // Request time, not build time (the catalog changes; the build machine may have no API)
  await connection();
  const defaultLocale = routing.defaultLocale;

  const pages: MetadataRoute.Sitemap = PAGES.map((path) => ({
    url: url(defaultLocale, path),
    alternates: { languages: languageAlternates(path) },
    changeFrequency: path === "/" ? "daily" : "monthly",
    priority: path === "/" ? 1 : 0.5,
  }));

  const [categories, authors] = await Promise.all([
    fetchJson<CategoryNode[]>("/categories"),
    fetchJson<Entry[]>("/sitemap/authors"),
  ]);

  const categoryIds: string[] = [];
  const walk = (nodes: CategoryNode[]) =>
    nodes.forEach((node) => {
      categoryIds.push(node.id);
      walk(node.children);
    });
  walk(categories ?? []);

  return [
    ...pages,
    ...categoryIds.map((id) => ({
      url: url(defaultLocale, `/categories/${id}`),
      alternates: { languages: languageAlternates(`/categories/${id}`) },
      changeFrequency: "weekly" as const,
      priority: 0.6,
    })),
    ...(authors ?? []).map((author) => ({
      url: url(defaultLocale, `/authors/${author.id}`),
      lastModified: author.updatedAt,
      alternates: { languages: languageAlternates(`/authors/${author.id}`) },
      priority: 0.4,
    })),
  ];
}

/** null when the API can't be reached: the sitemap then lists just the fixed pages. */
async function fetchJson<T>(path: string): Promise<T | null> {
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      headers: { Accept: "application/json" },
    });
    return response.ok ? ((await response.json()) as T) : null;
  } catch {
    return null;
  }
}
