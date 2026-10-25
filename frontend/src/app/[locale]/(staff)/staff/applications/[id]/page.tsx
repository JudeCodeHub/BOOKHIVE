import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { ApplicationReview } from "@/features/staff/application-review";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffApplications.detail");
  return { title: t("metaTitle") };
}

/** The id is read in the browser (useParams): the data needs the staff member's token anyway. */
export default function ApplicationReviewPage() {
  return (
    <Suspense>
      <ApplicationReview />
    </Suspense>
  );
}
