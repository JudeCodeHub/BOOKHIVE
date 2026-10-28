import { Suspense } from "react";
import type { Metadata } from "next";
import { ChevronLeft, ChevronRight, Newspaper } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { HexLoader } from "@/components/brand/hex-loader";
import { getNewsPage } from "@/features/news/data";
import { NewsCard } from "@/features/news/news-card";
import { Link } from "@/i18n/navigation";

// Checked in development for instant navigation (next.config: manual-warning)
export const instant = true;

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("News");
  return { title: t("metaTitle"), description: t("intro") };
}

/**
 * Library news (checklist step 346): rendered on the server (search engines, works without
 * JavaScript), pinned posts first. Pages are plain links (?page=2).
 */
export default async function NewsPage(props: PageProps<"/[locale]/news">) {
  const t = await getTranslations("News");
  const tCommon = await getTranslations("Common");
  return (
    <div className="mx-auto grid w-full max-w-4xl gap-6 px-4 py-8">
      <div className="grid gap-1">
        <h1 className="font-heading text-3xl font-semibold">{t("title")}</h1>
        <p className="text-muted-foreground">{t("intro")}</p>
      </div>
      {/* The page number is in the URL, only known per request: the shell above is static */}
      <Suspense
        fallback={
          <div className="flex justify-center py-16">
            <HexLoader label={tCommon("loading")} />
          </div>
        }
      >
        <NewsList {...props} />
      </Suspense>
    </div>
  );
}

async function NewsList({ searchParams }: PageProps<"/[locale]/news">) {
  const t = await getTranslations("News");
  const raw = (await searchParams).page;
  const asked = Number(Array.isArray(raw) ? raw[0] : raw);
  const page = Number.isInteger(asked) && asked >= 1 && asked <= 1000 ? asked - 1 : 0;
  const news = await getNewsPage(page);

  if (news.content.length === 0) {
    return (
      <div className="grid justify-items-center gap-2 py-16 text-center text-muted-foreground">
        <Newspaper aria-hidden className="size-10" />
        <p>{page === 0 ? t("empty") : t("noSuchPage")}</p>
        {page > 0 && (
          <Link href="/news" className="font-medium text-primary underline">
            {t("firstPage")}
          </Link>
        )}
      </div>
    );
  }

  const href = (p: number) => (p === 0 ? "/news" : `/news?page=${p + 1}`);
  return (
    <div className="grid gap-6">
      <ul className="grid gap-4">
        {news.content.map((post) => (
          <li key={post.id}>
            <NewsCard post={post} />
          </li>
        ))}
      </ul>
      {news.totalPages > 1 && (
        <nav aria-label={t("pages")} className="flex items-center justify-between gap-2 text-sm">
          {page > 0 ? (
            <Link href={href(page - 1)} className="inline-flex items-center gap-1 hover:underline">
              <ChevronLeft aria-hidden className="size-4" />
              {t("newer")}
            </Link>
          ) : (
            <span />
          )}
          <span className="text-muted-foreground">
            {t("pageOf", { page: page + 1, total: news.totalPages })}
          </span>
          {page + 1 < news.totalPages ? (
            <Link href={href(page + 1)} className="inline-flex items-center gap-1 hover:underline">
              {t("older")}
              <ChevronRight aria-hidden className="size-4" />
            </Link>
          ) : (
            <span />
          )}
        </nav>
      )}
    </div>
  );
}
