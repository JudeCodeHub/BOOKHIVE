import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { StaffAccounts } from "@/features/admin/staff-accounts";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("AdminStaff");
  return { title: t("title") };
}

export default function StaffPage() {
  const t = useTranslations("AdminStaff");

  return (
    <div className="grid gap-4">
      <div>
        <h1 className="text-xl font-semibold">{t("title")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{t("description")}</p>
      </div>
      <StaffAccounts />
    </div>
  );
}
