import { Suspense } from "react";
import type { Metadata } from "next";
import { BookOpen } from "lucide-react";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { BookAvailability } from "@/features/catalog/book-availability";
import { coverUrl } from "@/features/catalog/cover";
import { getPublicBook, type Book } from "@/features/catalog/data";
import { Link } from "@/i18n/navigation";
import { routing } from "@/i18n/routing";

/**
 * Public book page (checklist step 184). Rendered on the server so search engines and link
 * previews get the full content; the book data is cached for a few minutes (data.ts) and the
 * availability box loads fresh in the browser.
 */
export async function generateMetadata({
  params,
}: PageProps<"/[locale]/books/[id]">): Promise<Metadata> {
  const { locale, id } = await params;
  const book = await getPublicBook(id);
  // Here, not only in the page: crawlers then get a real 404 status
  if (!book) notFound();

  const authors = book.contributors.filter((c) => c.role === "AUTHOR").map((c) => c.name);
  const title = authors.length > 0 ? `${book.title} – ${authors.join(", ")}` : book.title;
  const description = (book.description ?? [book.subtitle, ...authors].filter(Boolean).join(" · "))
    .replace(/\s+/g, " ")
    .slice(0, 160);
  const cover = coverUrl(book.id, book.coverVersion, "large");

  return {
    title,
    description,
    // The same book in the other languages of the site
    alternates: {
      canonical: `/${locale}/books/${id}`,
      languages: Object.fromEntries(routing.locales.map((l) => [l, `/${l}/books/${id}`])),
    },
    openGraph: {
      type: "book",
      title,
      description,
      isbn: book.isbn13 ?? undefined,
      authors,
      images: cover ? [{ url: cover, width: 800, alt: book.title }] : undefined,
    },
  };
}

export default function BookPage(props: PageProps<"/[locale]/books/[id]">) {
  return (
    // The page shell (header, footer) is static; the book streams in for its id
    <Suspense fallback={<BookSkeleton />}>
      <BookDetails {...props} />
    </Suspense>
  );
}

async function BookDetails({ params }: PageProps<"/[locale]/books/[id]">) {
  const { locale, id } = await params;
  const book = await getPublicBook(id);
  if (!book) notFound();

  const t = await getTranslations("BookPage");
  const tFormats = await getTranslations("StaffCatalog.formats");
  const cover = coverUrl(book.id, book.coverVersion, "medium");
  const authors = book.contributors.filter((c) => c.role === "AUTHOR");
  const others = book.contributors.filter((c) => c.role !== "AUTHOR");
  const categoryName = (c: Book["categories"][number]) =>
    locale === "si" ? c.nameSi : locale === "ta" ? c.nameTa : c.nameEn;
  const languageName = (["si", "ta", "en"] as const).find((l) => l === book.language);

  return (
    <article className="mx-auto grid w-full max-w-5xl gap-8 px-4 py-8 md:grid-cols-[16rem_1fr]">
      <script
        type="application/ld+json"
        // Structured data for search engines; "<" escaped so the JSON can't close the tag
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(bookJsonLd(book)).replace(/</g, "\\u003c"),
        }}
      />

      <div className="grid content-start gap-4">
        {cover ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={cover}
            alt={book.title}
            className="aspect-[2/3] w-48 rounded-md border object-cover shadow-md md:w-full"
          />
        ) : (
          <div className="flex aspect-[2/3] w-48 items-center justify-center rounded-md border bg-muted md:w-full">
            <BookOpen aria-hidden className="size-12 text-muted-foreground" />
          </div>
        )}
        <section aria-labelledby="availability" className="rounded-lg border p-4">
          <h2 id="availability" className="mb-2 text-sm font-semibold">
            {t("availability")}
          </h2>
          <BookAvailability bookId={book.id} callNumber={book.callNumber ?? undefined} />
        </section>
      </div>

      <div className="grid content-start gap-6">
        <header className="grid gap-1">
          <h1 className="font-heading text-3xl font-semibold">{book.title}</h1>
          {book.subtitle && <p className="text-lg text-muted-foreground">{book.subtitle}</p>}
          {authors.length > 0 && (
            <p className="text-lg">
              {t.rich("by", { names: () => <PeopleLinks people={authors} /> })}
            </p>
          )}
          {others.length > 0 && (
            <p className="text-sm text-muted-foreground">
              <PeopleLinks people={others} withRole={(role) => t(`roles.${role}`)} />
            </p>
          )}
        </header>

        {book.description && (
          <section aria-labelledby="about">
            <h2 id="about" className="mb-2 font-semibold">
              {t("description")}
            </h2>
            <p className="leading-relaxed whitespace-pre-line">{book.description}</p>
          </section>
        )}

        <section aria-labelledby="details">
          <h2 id="details" className="mb-2 font-semibold">
            {t("details")}
          </h2>
          <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1.5 text-sm">
            {book.publisher && <Detail label={t("publisher")}>{book.publisher.name}</Detail>}
            {book.publishYear && <Detail label={t("year")}>{book.publishYear}</Detail>}
            {book.edition && <Detail label={t("edition")}>{book.edition}</Detail>}
            {book.pages && <Detail label={t("pages")}>{book.pages}</Detail>}
            <Detail label={t("language")}>
              {languageName ? t(`languages.${languageName}`) : book.language}
            </Detail>
            <Detail label={t("format")}>{tFormats(book.format)}</Detail>
            {book.isbn13 && (
              <Detail label={t("isbn")}>
                <span className="font-mono">{book.isbn13}</span>
              </Detail>
            )}
            {book.callNumber && (
              <Detail label={t("callNumber")}>
                <span className="font-mono">{book.callNumber}</span>
              </Detail>
            )}
            {book.categories.length > 0 && (
              <Detail label={t("categories")}>
                {book.categories.map((c, i) => (
                  <span key={c.id}>
                    {i > 0 && ", "}
                    <Link
                      href={`/categories/${c.id}`}
                      className="underline-offset-4 hover:underline"
                    >
                      {categoryName(c)}
                    </Link>
                  </span>
                ))}
              </Detail>
            )}
          </dl>
        </section>
      </div>
    </article>
  );
}

/** Names linking to their author pages, separated by commas. */
function PeopleLinks({
  people,
  withRole,
}: {
  people: Book["contributors"];
  withRole?: (role: Book["contributors"][number]["role"]) => string;
}) {
  return (
    <>
      {people.map((person, i) => (
        <span key={`${person.authorId}-${person.role}`}>
          {i > 0 && ", "}
          <Link href={`/authors/${person.authorId}`} className="underline-offset-4 hover:underline">
            {person.name}
          </Link>
          {withRole && ` (${withRole(person.role)})`}
        </span>
      ))}
    </>
  );
}

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <>
      <dt className="text-muted-foreground">{label}</dt>
      <dd>{children}</dd>
    </>
  );
}

/** schema.org Book: lets search engines show title, author and ISBN in results. */
function bookJsonLd(book: Book) {
  const cover = coverUrl(book.id, book.coverVersion, "large");
  return {
    "@context": "https://schema.org",
    "@type": "Book",
    name: book.title,
    alternativeHeadline: book.subtitle ?? undefined,
    author: book.contributors
      .filter((c) => c.role === "AUTHOR")
      .map((c) => ({ "@type": "Person", name: c.name })),
    isbn: book.isbn13 ?? undefined,
    numberOfPages: book.pages ?? undefined,
    inLanguage: book.language,
    datePublished: book.publishYear ? String(book.publishYear) : undefined,
    publisher: book.publisher ? { "@type": "Organization", name: book.publisher.name } : undefined,
    image: cover ?? undefined,
    description: book.description ?? undefined,
  };
}

function BookSkeleton() {
  return (
    <div
      className="mx-auto grid w-full max-w-5xl animate-pulse gap-8 px-4 py-8 md:grid-cols-[16rem_1fr]"
      aria-hidden
    >
      <div className="aspect-[2/3] w-48 rounded-md bg-muted md:w-full" />
      <div className="grid content-start gap-3">
        <div className="h-8 w-2/3 rounded bg-muted" />
        <div className="h-5 w-1/3 rounded bg-muted" />
        <div className="h-24 rounded bg-muted" />
      </div>
    </div>
  );
}
