import { Suspense } from "react";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { StaffLoans } from "@/features/loans/staff-loans";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffLoans");
  return { title: t("metaTitle") };
}

export default function StaffLoansPage() {
  const t = useTranslations("StaffLoans");

  return (
    <div className="grid gap-4">
      <h1 className="text-xl font-semibold">{t("title")}</h1>
      {/* Reads ?view= and ?page= from the URL, which is only known in the browser */}
      <Suspense>
        <StaffLoans />
      </Suspense>
    </div>
  );
}
