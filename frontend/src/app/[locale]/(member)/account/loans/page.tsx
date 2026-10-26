import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { MyLoans } from "@/features/loans/my-loans";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("MyLoans");
  return { title: t("metaTitle") };
}

export default function MyLoansPage() {
  const t = useTranslations("MyLoans");

  return (
    <div className="grid gap-6">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <MyLoans />
    </div>
  );
}
