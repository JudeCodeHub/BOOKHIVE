import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { ReadingHistoryPage } from "@/features/loans/reading-history";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("ReadingHistory");
  return { title: t("metaTitle") };
}

export default function HistoryPage() {
  const t = useTranslations("ReadingHistory");

  return (
    <div className="grid gap-6">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <ReadingHistoryPage />
    </div>
  );
}
