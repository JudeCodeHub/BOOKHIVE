"use client";

import { Suspense } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Link, usePathname } from "@/i18n/navigation";
import { LANGUAGES } from "@/i18n/languages";
import { cn } from "@/lib/utils";

/**
 * English / Sinhala / Tamil switcher: links to the same page in another language, e.g.
 * /en/search?q=x -> /si/search?q=x. The proxy then remembers the choice in a cookie.
 */
export function LanguageSwitcher() {
  return (
    // Three levels, from most to least known when the page HTML is pre-rendered:
    // - no path yet (pages with an [id] in the URL): links go to the other language's home page
    // - path known: links keep the page
    // - query known too (browser only): links keep the page and its query (?q=...)
    // Every level works without JavaScript.
    <Suspense fallback={<LanguageLinks pathname="/" />}>
      <LanguageLinksForPath />
    </Suspense>
  );
}

function LanguageLinksForPath() {
  const pathname = usePathname();
  return (
    <Suspense fallback={<LanguageLinks pathname={pathname} />}>
      <LanguageLinksWithQuery pathname={pathname} />
    </Suspense>
  );
}

function LanguageLinksWithQuery({ pathname }: { pathname: string }) {
  const searchParams = useSearchParams();
  return <LanguageLinks pathname={pathname} query={Object.fromEntries(searchParams)} />;
}

function LanguageLinks({ pathname, query }: { pathname: string; query?: Record<string, string> }) {
  const t = useTranslations("Header");
  const current = useLocale();

  return (
    <nav aria-label={t("language")}>
      <ul className="flex rounded-md border p-0.5 text-sm">
        {LANGUAGES.map((lang) => (
          <li key={lang.code}>
            {lang.code === current ? (
              <span
                lang={lang.code}
                title={lang.name}
                aria-current="true"
                className="block rounded-sm bg-primary px-2 py-1 font-medium text-primary-foreground"
              >
                {lang.label}
              </span>
            ) : (
              <Link
                href={{ pathname, query }}
                locale={lang.code}
                lang={lang.code}
                hrefLang={lang.code}
                title={lang.name}
                // Screen readers read the full name ("සිංහල"), not the short label
                aria-label={lang.name}
                className={cn(
                  "block rounded-sm px-2 py-1 text-muted-foreground transition-colors",
                  "hover:bg-accent hover:text-accent-foreground",
                )}
              >
                {lang.label}
              </Link>
            )}
          </li>
        ))}
      </ul>
    </nav>
  );
}
