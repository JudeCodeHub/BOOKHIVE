import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { MembershipPanel } from "@/features/membership/membership-panel";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Membership");
  return { title: t("metaTitle") };
}

export default function MembershipPage() {
  const t = useTranslations("Membership");

  return (
    <div className="grid max-w-2xl gap-6">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <MembershipPanel />
    </div>
  );
}
