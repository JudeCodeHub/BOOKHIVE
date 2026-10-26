import type { Metadata } from "next";
import { ArrowLeft } from "lucide-react";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { CatalogImport } from "@/features/staff/catalog-import";
import { Link } from "@/i18n/navigation";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("CatalogImport");
  return { title: t("metaTitle") };
}

export default function CatalogImportPage() {
  const t = useTranslations("CatalogImport");

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
      <CatalogImport />
    </div>
  );
}
