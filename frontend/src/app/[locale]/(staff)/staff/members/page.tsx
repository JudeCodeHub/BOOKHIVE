import { Suspense } from "react";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { MemberSearch } from "@/features/staff/member-search";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffMembers");
  return { title: t("metaTitle") };
}

export default function MembersPage() {
  const t = useTranslations("StaffMembers");

  return (
    <div className="grid gap-4">
      <h1 className="text-xl font-semibold">{t("title")}</h1>
      {/* Reads ?q= from the URL, which is only known in the browser */}
      <Suspense>
        <MemberSearch />
      </Suspense>
    </div>
  );
}
