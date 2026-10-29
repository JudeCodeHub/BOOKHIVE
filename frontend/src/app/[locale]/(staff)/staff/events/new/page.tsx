import { BackLink } from "@/components/layout/back-link";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { StaffEventForm } from "@/features/events/staff-event-form";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffEvents");
  return { title: t("newTitle") };
}

export default function NewEventPage() {
  const t = useTranslations("StaffEvents");

  return (
    <div className="grid gap-4">
      <BackLink href="/staff/events">{t("back")}</BackLink>
      <div>
        <h1 className="text-xl font-semibold">{t("newTitle")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{t("newIntro")}</p>
      </div>
      <StaffEventForm />
    </div>
  );
}
