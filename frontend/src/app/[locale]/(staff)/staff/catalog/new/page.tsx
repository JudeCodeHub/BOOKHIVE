import { BackLink } from "@/components/layout/back-link";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { BookForm } from "@/features/staff/book-form";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("BookForm");
  return { title: t("metaTitle") };
}

export default function NewBookPage() {
  const t = useTranslations("BookForm");

  return (
    <div className="grid gap-4">
      <BackLink href="/staff/catalog">{t("back")}</BackLink>
      <h1 className="text-xl font-semibold">{t("title")}</h1>
      <BookForm />
    </div>
  );
}
