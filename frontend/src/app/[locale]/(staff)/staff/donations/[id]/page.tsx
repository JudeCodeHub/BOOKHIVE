import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { DonationPage } from "@/features/staff/donations";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Donations");
  return { title: t("metaTitle") };
}

/** The id is read in the browser (useParams). */
export default function StaffDonationPage() {
  return (
    <Suspense>
      <DonationPage />
    </Suspense>
  );
}
