import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { HoldShelf } from "@/features/holds/hold-shelf";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("HoldShelf");
  return { title: t("metaTitle") };
}

export default function HoldShelfPage() {
  const t = useTranslations("HoldShelf");

  return (
    <div className="grid gap-4">
      <h1 className="text-xl font-semibold">{t("title")}</h1>
      <HoldShelf />
    </div>
  );
}
