import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { StaffNews } from "@/features/news/staff-news";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffNews");
  return { title: t("metaTitle") };
}

export default function StaffNewsListPage() {
  const t = useTranslations("StaffNews");

  return (
    <div className="grid gap-4">
      <div>
        <h1 className="text-xl font-semibold">{t("metaTitle")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{t("intro")}</p>
      </div>
      <StaffNews />
    </div>
  );
}
