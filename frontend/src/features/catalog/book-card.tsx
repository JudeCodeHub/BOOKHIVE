import { BookOpen } from "lucide-react";
import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { coverUrl } from "@/features/catalog/cover";
import { Link } from "@/i18n/navigation";
import type { Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type BookSummary = Schema<"BookSummary">;

/**
 * A book in a list (checklist step 183): cover, title, authors, year and whether a copy can be
 * borrowed now. Used by search results and book lists on the public site. Works in server and
 * client components (no state of its own).
 */
export function BookCard({ book, className }: { book: BookSummary; className?: string }) {
  const t = useTranslations("BookCard");
  const cover = coverUrl(book.id, book.coverVersion, "medium");

  return (
    <article
      className={cn(
        "group relative flex gap-3 rounded-lg border bg-card p-3 transition-shadow hover:shadow-md sm:flex-col",
        className,
      )}
    >
      {cover ? (
        // Public cover from our API (cached a year by its ?v= URL)
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={cover}
          alt=""
          loading="lazy"
          className="aspect-[2/3] w-20 shrink-0 rounded object-cover sm:w-full"
        />
      ) : (
        <div
          role="img"
          aria-label={t("noCover")}
          className="flex aspect-[2/3] w-20 shrink-0 items-center justify-center rounded bg-muted sm:w-full"
        >
          <BookOpen aria-hidden className="size-8 text-muted-foreground" />
        </div>
      )}
      <div className="grid min-w-0 content-start gap-1">
        <h3 className="line-clamp-2 font-heading font-semibold">
          {/* The link covers the whole card (::after), so the card is one big click target */}
          <Link
            href={`/books/${book.id}`}
            className="group-has-focus-visible:ring-3 group-has-focus-visible:ring-ring/50 after:absolute after:inset-0 after:rounded-lg focus-visible:outline-none"
          >
            {book.title}
          </Link>
        </h3>
        {book.contributors.length > 0 && (
          <p className="line-clamp-1 text-sm text-muted-foreground">
            {t("by", { names: book.contributors.join(", ") })}
          </p>
        )}
        {book.publishYear && <p className="text-xs text-muted-foreground">{book.publishYear}</p>}
        <AvailabilityBadge copies={book.copies} available={book.availableToBorrow} />
      </div>
    </article>
  );
}

/** Green when one can be borrowed now, amber when all are out, grey when the library has none. */
function AvailabilityBadge({ copies, available }: { copies: number; available: number }) {
  const t = useTranslations("BookCard");
  if (copies === 0) return <Badge variant="outline">{t("notInStock")}</Badge>;
  if (available === 0) return <Badge variant="on-loan">{t("allOnLoan")}</Badge>;
  return <Badge variant="available">{t("availableCount", { count: available })}</Badge>;
}
