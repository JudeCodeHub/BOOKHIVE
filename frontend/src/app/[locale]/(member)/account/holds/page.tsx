import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { MyHolds } from "@/features/holds/my-holds";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("MyHolds");
  return { title: t("metaTitle") };
}

export default function MyHoldsPage() {
  const t = useTranslations("MyHolds");

  return (
    <div className="grid gap-6">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <MyHolds />
    </div>
  );
}
