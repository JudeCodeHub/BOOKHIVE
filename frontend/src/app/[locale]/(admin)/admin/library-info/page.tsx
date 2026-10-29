import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { LibraryInfoAdmin } from "@/features/admin/library-info";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("AdminLibraryInfo");
  return { title: t("title") };
}

export default function LibraryInfoPage() {
  const t = useTranslations("AdminLibraryInfo");

  return (
    <div className="grid gap-4">
      <div>
        <h1 className="text-xl font-semibold">{t("title")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{t("description")}</p>
      </div>
      <LibraryInfoAdmin />
    </div>
  );
}
