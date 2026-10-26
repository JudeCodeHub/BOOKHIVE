import type { Metadata } from "next";
import { ArrowLeft } from "lucide-react";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { BookForm } from "@/features/staff/book-form";
import { Link } from "@/i18n/navigation";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("BookForm");
  return { title: t("metaTitle") };
}

export default function NewBookPage() {
  const t = useTranslations("BookForm");

  return (
    <div className="grid gap-4">
      <Link
        href="/staff/catalog"
        className="inline-flex items-center gap-1 justify-self-start text-sm text-muted-foreground underline-offset-4 hover:underline"
      >
        <ArrowLeft aria-hidden className="size-4" />
        {t("back")}
      </Link>
      <h1 className="text-xl font-semibold">{t("title")}</h1>
      <BookForm />
    </div>
  );
}
