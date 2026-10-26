import { getFormatter, getLocale, getTranslations } from "next-intl/server";
import { LAST_UPDATED, type LegalDocument, type Locale } from "@/content/legal";
import { Link } from "@/i18n/navigation";

/** A legal document in the page's language (checklist step 213). Static: no data to load. */
export async function LegalPage({ documents }: { documents: Record<Locale, LegalDocument> }) {
  const locale = (await getLocale()) as Locale;
  const doc = documents[locale] ?? documents.en;
  const t = await getTranslations("Legal");
  const format = await getFormatter();

  return (
    <article className="mx-auto grid w-full max-w-3xl gap-6 px-4 py-8">
      <header className="grid gap-2">
        <h1 className="font-heading text-3xl font-semibold">{doc.title}</h1>
        <p className="text-sm text-muted-foreground">
          {t("lastUpdated", {
            date: format.dateTime(new Date(`${LAST_UPDATED}T00:00:00Z`), {
              dateStyle: "long",
              timeZone: "UTC",
            }),
          })}
        </p>
        <p className="leading-relaxed">{doc.intro}</p>
      </header>
      {doc.sections.map((section) => (
        <section key={section.heading} className="grid gap-2">
          <h2 className="text-xl font-semibold">{section.heading}</h2>
          {section.paragraphs.map((paragraph) => (
            <p key={paragraph.slice(0, 40)} className="leading-relaxed">
              {paragraph}
            </p>
          ))}
        </section>
      ))}
      <p className="text-sm text-muted-foreground">
        {t.rich("questions", {
          visit: (chunks) => (
            <Link href="/visit" className="underline">
              {chunks}
            </Link>
          ),
        })}
      </p>
    </article>
  );
}
