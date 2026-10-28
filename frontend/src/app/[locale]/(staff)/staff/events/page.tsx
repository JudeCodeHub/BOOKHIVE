import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { StaffEvents } from "@/features/events/staff-events";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffEvents");
  return { title: t("metaTitle") };
}

export default function StaffEventsPage() {
  const t = useTranslations("StaffEvents");

  return (
    <div className="grid gap-4">
      <div>
        <h1 className="text-xl font-semibold">{t("metaTitle")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{t("intro")}</p>
      </div>
      <StaffEvents />
    </div>
  );
}
