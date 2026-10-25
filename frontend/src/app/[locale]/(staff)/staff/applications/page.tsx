import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { ApplicationsQueue } from "@/features/staff/applications-queue";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffApplications");
  return { title: t("metaTitle") };
}

export default function ApplicationsPage() {
  const t = useTranslations("StaffApplications");

  return (
    <div className="grid gap-4">
      <div>
        <h1 className="text-xl font-semibold">{t("title")}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t("description")}</p>
      </div>
      <ApplicationsQueue />
    </div>
  );
}
