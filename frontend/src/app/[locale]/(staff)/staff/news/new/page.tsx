import type { Metadata } from "next";
import { ArrowLeft } from "lucide-react";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { StaffNewsForm } from "@/features/news/staff-news-form";
import { Link } from "@/i18n/navigation";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffNews");
  return { title: t("newTitle") };
}

export default function NewNewsPage() {
  const t = useTranslations("StaffNews");

  return (
    <div className="grid gap-4">
      <Link
        href="/staff/news"
        className="inline-flex items-center gap-1 justify-self-start text-sm text-muted-foreground underline-offset-4 hover:underline"
      >
        <ArrowLeft aria-hidden className="size-4" />
        {t("back")}
      </Link>
      <div>
        <h1 className="text-xl font-semibold">{t("newTitle")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{t("newIntro")}</p>
      </div>
      <StaffNewsForm />
    </div>
  );
}
