import { Suspense } from "react";
import type { Metadata } from "next";
import { ChevronRight } from "lucide-react";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { HexLoader } from "@/components/brand/hex-loader";
import { BookResults } from "@/features/catalog/book-results";
import { getCategoryPath, type CategoryNode } from "@/features/catalog/data";
import { Link } from "@/i18n/navigation";
import { routing } from "@/i18n/routing";

function nameIn(locale: string, node: CategoryNode) {
  return locale === "si" ? node.nameSi : locale === "ta" ? node.nameTa : node.nameEn;
}

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/categories/[id]">): Promise<Metadata> {
  const { locale, id } = await params;
  const path = await getCategoryPath(id);
  if (!path) notFound();
  const name = nameIn(locale, path.at(-1)!);
  const t = await getTranslations("CategoryPage");
  return {
    title: name,
    description: t("metaDescription", { name }),
    alternates: {
      canonical: `/${locale}/categories/${id}`,
      languages: Object.fromEntries(routing.locales.map((l) => [l, `/${l}/categories/${id}`])),
    },
  };
}

/**
 * Category page (checklist step 203): breadcrumbs, its subcategories, then its books (including
 * those in subcategories). Header rendered on the server from the cached tree; books in the browser.
 */
export default function CategoryPage(props: PageProps<"/[locale]/categories/[id]">) {
  return (
    <div className="mx-auto grid w-full max-w-6xl gap-6 px-4 py-8">
      <Suspense fallback={<div className="h-24 animate-pulse rounded-lg bg-muted" aria-hidden />}>
        <CategoryHeader {...props} />
      </Suspense>
    </div>
  );
}

async function CategoryHeader({ params }: PageProps<"/[locale]/categories/[id]">) {
  const { locale, id } = await params;
  const path = await getCategoryPath(id);
  if (!path) notFound();
  const t = await getTranslations("CategoryPage");
  const tCommon = await getTranslations("Common");
  const category = path.at(-1)!;
  const name = nameIn(locale, category);

  return (
    <>
      <nav aria-label={t("breadcrumb")}>
        <ol className="flex flex-wrap items-center gap-1 text-sm text-muted-foreground">
          {path.map((node, i) => (
            <li key={node.id} className="flex items-center gap-1">
              {i > 0 && <ChevronRight aria-hidden className="size-3" />}
              {i === path.length - 1 ? (
                <span aria-current="page" className="text-foreground">
                  {nameIn(locale, node)}
                </span>
              ) : (
                <Link
                  href={`/categories/${node.id}`}
                  className="underline-offset-4 hover:underline"
                >
                  {nameIn(locale, node)}
                </Link>
              )}
            </li>
          ))}
        </ol>
      </nav>
      <header className="grid gap-1">
        <h1 className="font-heading text-3xl font-semibold">
          {name}
          {category.ddcCode && (
            <span className="ml-3 font-mono text-lg font-normal text-muted-foreground">
              {category.ddcCode}
            </span>
          )}
        </h1>
        <p className="text-sm text-muted-foreground">{t(`sections.${category.section}`)}</p>
      </header>

      {category.children.length > 0 && (
        <section aria-labelledby="subcategories">
          <h2 id="subcategories" className="mb-2 font-semibold">
            {t("subcategories")}
          </h2>
          <ul className="flex flex-wrap gap-2">
            {category.children.map((child) => (
              <li key={child.id}>
                <Link
                  href={`/categories/${child.id}`}
                  className="inline-flex items-center gap-2 rounded-full border px-3 py-1 text-sm hover:border-primary hover:bg-accent"
                >
                  {child.ddcCode && (
                    <span className="font-mono text-xs text-muted-foreground">{child.ddcCode}</span>
                  )}
                  {nameIn(locale, child)}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section aria-labelledby="category-books" className="grid gap-3">
        <h2 id="category-books" className="text-xl font-semibold">
          {t("books", { name })}
        </h2>
        {/* Reads ?page=&sort= in the browser */}
        <Suspense fallback={<HexLoader label={tCommon("loading")} />}>
          <BookResults filter={{ category: id }} />
        </Suspense>
      </section>
    </>
  );
}
