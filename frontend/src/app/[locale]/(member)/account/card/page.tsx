import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { LibraryCard } from "@/features/membership/library-card";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("LibraryCard");
  return { title: t("metaTitle") };
}

export default function LibraryCardPage() {
  const t = useTranslations("LibraryCard");

  return (
    <div className="grid gap-6">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <LibraryCard />
    </div>
  );
}
