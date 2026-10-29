import { Suspense } from "react";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { ReportsPage } from "@/features/reports/reports-page";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Reports");
  return { title: t("title") };
}

export default function AdminReportsPage() {
  const t = useTranslations("Reports");

  return (
    <div className="grid gap-4">
      <div>
        <h1 className="text-xl font-semibold">{t("title")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{t("description")}</p>
      </div>
      {/* The report and range are read from the address, only known in the browser */}
      <Suspense>
        <ReportsPage />
      </Suspense>
    </div>
  );
}
