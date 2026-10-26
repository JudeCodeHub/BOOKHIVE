import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { VisitDetails } from "@/features/library/visit-details";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Library");
  return { title: t("metaTitle") };
}

/** Visit page (checklist step 206): hours, holidays, address, phone, email and a map. */
export default function VisitPage() {
  const t = useTranslations("Library");

  return (
    <div className="mx-auto grid w-full max-w-6xl gap-6 px-4 py-8">
      <h1 className="font-heading text-3xl font-semibold">{t("title")}</h1>
      <VisitDetails />
    </div>
  );
}
