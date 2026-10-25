import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { MemberProfile } from "@/features/staff/member-profile";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffMembers");
  return { title: t("metaTitle") };
}

/** The id is read in the browser (useParams): the data needs the staff member's token anyway. */
export default function MemberProfilePage() {
  return (
    <Suspense>
      <MemberProfile />
    </Suspense>
  );
}
