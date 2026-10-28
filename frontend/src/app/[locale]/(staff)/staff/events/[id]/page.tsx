import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { StaffEventPage } from "@/features/events/staff-event-page";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffEvents");
  return { title: t("metaTitle") };
}

/** The id is read in the browser (useParams). */
export default function Page() {
  return (
    <Suspense>
      <StaffEventPage />
    </Suspense>
  );
}
