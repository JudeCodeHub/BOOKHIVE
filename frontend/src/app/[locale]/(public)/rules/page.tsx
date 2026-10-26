import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { RulesContent } from "@/features/library/rules-content";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Rules");
  return { title: t("metaTitle") };
}

/** Borrowing rules and FAQ (checklist step 208), generated from the system's own settings. */
export default function RulesPage() {
  const t = useTranslations("Rules");

  return (
    <div className="mx-auto grid w-full max-w-6xl gap-4 px-4 py-8">
      <h1 className="font-heading text-3xl font-semibold">{t("title")}</h1>
      <p className="max-w-3xl text-muted-foreground">{t("intro")}</p>
      <RulesContent />
    </div>
  );
}
