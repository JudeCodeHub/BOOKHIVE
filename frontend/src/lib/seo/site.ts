import { routing } from "@/i18n/routing";

/** The website's public address (NEXT_PUBLIC_SITE_URL), without a trailing slash. */
export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000").replace(
  /\/$/,
  "",
);

/** Absolute URL of a page in a language, e.g. url("si", "/books/123"). */
export function url(locale: string, path: string) {
  return `${SITE_URL}/${locale}${path === "/" ? "" : path}`;
}

/** The same page in every language, for sitemap and hreflang links. */
export function languageAlternates(path: string) {
  return Object.fromEntries(routing.locales.map((locale) => [locale, url(locale, path)]));
}

/** How many book sitemap files there are (40,000 books each, so up to 200,000 books). */
export const BOOK_SITEMAPS = 5;
export const BOOKS_PER_SITEMAP = 40_000;
