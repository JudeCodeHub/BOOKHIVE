import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { ContactForm } from "@/features/library/contact-form";
import { VisitDetails } from "@/features/library/visit-details";

// Checked in development for instant navigation (next.config: manual-warning)
export const instant = true;

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Library");
  return { title: t("metaTitle") };
}

/** Visit page (checklist steps 206-207): hours, holidays, contact details, map, contact form. */
export default function VisitPage() {
  const t = useTranslations("Library");
  const tContact = useTranslations("Contact");

  return (
    <div className="mx-auto grid w-full max-w-6xl gap-6 px-4 py-8">
      <h1 className="font-heading text-3xl font-semibold">{t("title")}</h1>
      <VisitDetails />
      <section aria-labelledby="contact-form" className="grid max-w-2xl gap-3">
        <h2 id="contact-form" className="text-xl font-semibold">
          {tContact("title")}
        </h2>
        <ContactForm />
      </section>
    </div>
  );
}
