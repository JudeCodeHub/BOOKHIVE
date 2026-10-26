import { Suspense } from "react";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { CatalogList } from "@/features/staff/catalog-list";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffCatalog");
  return { title: t("metaTitle") };
}

export default function CatalogPage() {
  const t = useTranslations("StaffCatalog");

  return (
    <div className="grid gap-4">
      <h1 className="text-xl font-semibold">{t("title")}</h1>
      {/* Filters are read from the URL, which is only known in the browser */}
      <Suspense>
        <CatalogList />
      </Suspense>
    </div>
  );
}
