import { Suspense } from "react";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { getFormatter, getTranslations } from "next-intl/server";
import { RichText } from "@/components/common/rich-text";
import { getNewsPost, type NewsPost } from "@/features/news/data";
import { newsImageUrl } from "@/features/news/news-card";
import { Link } from "@/i18n/navigation";
import { routing } from "@/i18n/routing";

// Checked in development for instant navigation (next.config: manual-warning)
export const instant = true;

/** A news post (checklist step 346): server-rendered and cached for a few minutes. */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/news/[id]">): Promise<Metadata> {
  const { locale, id } = await params;
  const post = await getNewsPost(id);
  if (!post) notFound();

  const image = newsImageUrl(post);
  const description = post.summary?.slice(0, 160) ?? undefined;
  return {
    title: post.title,
    description,
    alternates: {
      canonical: `/${locale}/news/${id}`,
      languages: Object.fromEntries(routing.locales.map((l) => [l, `/${l}/news/${id}`])),
    },
    openGraph: {
      type: "article",
      title: post.title,
      description,
      publishedTime: post.publishedAt ?? undefined,
      modifiedTime: post.updatedAt,
      images: image ? [{ url: image, alt: post.title }] : undefined,
    },
  };
}

export default function NewsPostPage(props: PageProps<"/[locale]/news/[id]">) {
  return (
    <Suspense fallback={<PostSkeleton />}>
      <PostDetails {...props} />
    </Suspense>
  );
}

async function PostDetails({ params }: PageProps<"/[locale]/news/[id]">) {
  const { id } = await params;
  const post = await getNewsPost(id);
  if (!post) notFound();

  const t = await getTranslations("News");
  const format = await getFormatter();
  const image = newsImageUrl(post);
  // "Updated" only when it was edited a while after publishing, not for the save that published it
  const edited =
    post.publishedAt &&
    new Date(post.updatedAt).getTime() - new Date(post.publishedAt).getTime() > 60 * 60_000;

  return (
    <article className="mx-auto grid w-full max-w-3xl gap-6 px-4 py-8">
      <script
        type="application/ld+json"
        // Structured data for search engines; "<" escaped so the JSON can't close the tag
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(articleJsonLd(post)).replace(/</g, "\\u003c"),
        }}
      />
      <Link
        href="/news"
        className="flex w-fit items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft aria-hidden className="size-4" />
        {t("allNews")}
      </Link>
      <header className="grid gap-2">
        <h1 className="font-heading text-3xl font-semibold">{post.title}</h1>
        <p className="text-sm text-muted-foreground">
          {post.publishedAt && (
            <time dateTime={post.publishedAt}>
              {format.dateTime(new Date(post.publishedAt), { dateStyle: "long" })}
            </time>
          )}
          {edited && (
            <>
              {" · "}
              {t("updated", {
                date: format.dateTime(new Date(post.updatedAt), { dateStyle: "medium" }),
              })}
            </>
          )}
        </p>
        {post.summary && <p className="text-lg text-muted-foreground">{post.summary}</p>}
      </header>
      {image && (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={image}
          alt=""
          fetchPriority="high"
          className="w-full rounded-lg border object-cover"
        />
      )}
      <RichText html={post.bodyHtml} />
    </article>
  );
}

/** schema.org NewsArticle, so search engines can show the date and picture. */
function articleJsonLd(post: NewsPost) {
  return {
    "@context": "https://schema.org",
    "@type": "NewsArticle",
    headline: post.title,
    description: post.summary ?? undefined,
    datePublished: post.publishedAt ?? undefined,
    dateModified: post.updatedAt,
    image: newsImageUrl(post) ?? undefined,
    publisher: { "@type": "Organization", name: "BookHive" },
  };
}

function PostSkeleton() {
  return (
    <div className="mx-auto grid w-full max-w-3xl animate-pulse gap-6 px-4 py-8" aria-hidden>
      <div className="h-4 w-24 rounded bg-muted" />
      <div className="h-9 w-3/4 rounded bg-muted" />
      <div className="aspect-video rounded-lg bg-muted" />
      <div className="grid gap-2">
        <div className="h-4 rounded bg-muted" />
        <div className="h-4 rounded bg-muted" />
        <div className="h-4 w-2/3 rounded bg-muted" />
      </div>
    </div>
  );
}
