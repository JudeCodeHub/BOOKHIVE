import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { MySuggestions } from "@/features/membership/my-suggestions";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("MySuggestions");
  return { title: t("metaTitle") };
}

export default function SuggestionsPage() {
  const t = useTranslations("MySuggestions");

  return (
    <div className="grid gap-6">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <MySuggestions />
    </div>
  );
}
