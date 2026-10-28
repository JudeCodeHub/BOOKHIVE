import { Suspense } from "react";
import type { Metadata } from "next";
import { UserRound } from "lucide-react";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { HexLoader } from "@/components/brand/hex-loader";
import { BookResults } from "@/features/catalog/book-results";
import { getPublicAuthor } from "@/features/catalog/data";
import { routing } from "@/i18n/routing";

// Checked in development for instant navigation (next.config: manual-warning)
export const instant = true;

export async function generateMetadata({
  params,
}: PageProps<"/[locale]/authors/[id]">): Promise<Metadata> {
  const { locale, id } = await params;
  const author = await getPublicAuthor(id);
  if (!author) notFound();
  const t = await getTranslations("AuthorPage");
  return {
    title: author.name,
    description: (author.bio ?? t("metaDescription", { name: author.name })).slice(0, 160),
    alternates: {
      canonical: `/${locale}/authors/${id}`,
      languages: Object.fromEntries(routing.locales.map((l) => [l, `/${l}/authors/${id}`])),
    },
    openGraph: { type: "profile", title: author.name },
  };
}

/**
 * Author page (checklist step 204): name, bio, and every book they're on in the catalog (author,
 * editor, translator or illustrator). Header from the server (cached); books in the browser.
 */
export default function AuthorPage(props: PageProps<"/[locale]/authors/[id]">) {
  return (
    <div className="mx-auto grid w-full max-w-6xl gap-6 px-4 py-8">
      <Suspense fallback={<div className="h-24 animate-pulse rounded-lg bg-muted" aria-hidden />}>
        <AuthorDetails {...props} />
      </Suspense>
    </div>
  );
}

async function AuthorDetails({ params }: PageProps<"/[locale]/authors/[id]">) {
  const { id } = await params;
  const author = await getPublicAuthor(id);
  if (!author) notFound();
  const t = await getTranslations("AuthorPage");
  const tCommon = await getTranslations("Common");

  return (
    <>
      <script
        type="application/ld+json"
        // Structured data for search engines; "<" escaped so the JSON can't close the tag
        dangerouslySetInnerHTML={{
          __html: JSON.stringify({
            "@context": "https://schema.org",
            "@type": "Person",
            name: author.name,
            description: author.bio ?? undefined,
          }).replace(/</g, "\\u003c"),
        }}
      />
      <header className="flex items-start gap-4">
        <span className="flex size-16 shrink-0 items-center justify-center rounded-full bg-primary/15">
          <UserRound aria-hidden className="size-8 text-primary" />
        </span>
        <div className="grid gap-1">
          <h1 className="font-heading text-3xl font-semibold">{author.name}</h1>
          <p className="text-sm text-muted-foreground">{t("books", { count: author.bookCount })}</p>
        </div>
      </header>
      {author.bio && <p className="max-w-3xl leading-relaxed whitespace-pre-line">{author.bio}</p>}

      <section aria-labelledby="author-books" className="grid gap-3">
        <h2 id="author-books" className="text-xl font-semibold">
          {t("booksTitle")}
        </h2>
        <Suspense fallback={<HexLoader label={tCommon("loading")} />}>
          <BookResults filter={{ author: id }} />
        </Suspense>
      </section>
    </>
  );
}
