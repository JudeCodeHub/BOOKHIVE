import { Pin } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { API_BASE_URL } from "@/lib/api";
import { cn } from "@/lib/utils";

/** The post's picture (cached by the API; ?v= changes with a new picture), or null. */
export function newsImageUrl(post: { id: string; imageVersion?: string | null }): string | null {
  return post.imageVersion ? `${API_BASE_URL}/news/${post.id}/image?v=${post.imageVersion}` : null;
}

/**
 * A news post in a list (checklist step 346): picture, date, title and summary; the whole card
 * links to the post. Works in server and client components.
 */
export function NewsCard({
  post,
  className,
}: {
  post: {
    id: string;
    title: string;
    summary?: string | null;
    pinned: boolean;
    publishedAt?: string | null;
    imageVersion?: string | null;
  };
  className?: string;
}) {
  const t = useTranslations("News");
  const format = useFormatter();
  const image = newsImageUrl(post);

  return (
    <article
      className={cn(
        "group relative grid overflow-hidden rounded-lg border bg-card transition-shadow hover:shadow-md",
        image && "sm:grid-cols-[14rem_1fr]",
        className,
      )}
    >
      {image && (
        // News pictures are already resized JPEGs from the API, cached for a month
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={image}
          alt=""
          loading="lazy"
          className="aspect-video w-full object-cover sm:aspect-auto sm:h-full"
        />
      )}
      <div className="grid content-start gap-2 p-4">
        <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          {post.pinned && (
            <span className="inline-flex items-center gap-1 font-medium text-primary">
              <Pin aria-hidden className="size-3.5" />
              {t("pinned")}
            </span>
          )}
          {post.publishedAt && (
            <time dateTime={post.publishedAt}>
              {format.dateTime(new Date(post.publishedAt), { dateStyle: "long" })}
            </time>
          )}
        </div>
        <h3 className="font-heading text-lg font-semibold">
          <Link
            href={`/news/${post.id}`}
            className="group-has-focus-visible:ring-3 group-has-focus-visible:ring-ring/50 after:absolute after:inset-0 after:rounded-lg focus-visible:outline-none"
          >
            {post.title}
          </Link>
        </h3>
        {post.summary && <p className="line-clamp-3 text-sm">{post.summary}</p>}
      </div>
    </article>
  );
}
