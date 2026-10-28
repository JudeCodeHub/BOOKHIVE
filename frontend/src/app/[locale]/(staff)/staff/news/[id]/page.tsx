import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { StaffNewsPage } from "@/features/news/staff-news-page";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffNews");
  return { title: t("metaTitle") };
}

/** The id is read in the browser (useParams). */
export default function Page() {
  return (
    <Suspense>
      <StaffNewsPage />
    </Suspense>
  );
}
