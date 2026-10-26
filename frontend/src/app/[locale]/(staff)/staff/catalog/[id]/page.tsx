import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { BookPage } from "@/features/staff/book-page";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffBook");
  return { title: t("metaTitle") };
}

/** The id and tab are read in the browser (useParams / useSearchParams). */
export default function StaffBookPage() {
  return (
    <Suspense>
      <BookPage />
    </Suspense>
  );
}
