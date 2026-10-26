import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { SuggestionsQueue } from "@/features/staff/suggestions-queue";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("SuggestionsQueue");
  return { title: t("metaTitle") };
}

export default function SuggestionsPage() {
  const t = useTranslations("SuggestionsQueue");

  return (
    <div className="grid gap-4">
      <div>
        <h1 className="text-xl font-semibold">{t("title")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{t("description")}</p>
      </div>
      <SuggestionsQueue />
    </div>
  );
}
