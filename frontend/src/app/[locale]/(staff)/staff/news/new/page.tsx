import { BackLink } from "@/components/layout/back-link";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { StaffNewsForm } from "@/features/news/staff-news-form";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffNews");
  return { title: t("newTitle") };
}

export default function NewNewsPage() {
  const t = useTranslations("StaffNews");

  return (
    <div className="grid gap-4">
      <BackLink href="/staff/news">{t("back")}</BackLink>
      <div>
        <h1 className="text-xl font-semibold">{t("newTitle")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{t("newIntro")}</p>
      </div>
      <StaffNewsForm />
    </div>
  );
}
