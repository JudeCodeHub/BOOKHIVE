import type { Metadata } from "next";
import { ArrowLeft } from "lucide-react";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { StaffEventForm } from "@/features/events/staff-event-form";
import { Link } from "@/i18n/navigation";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("StaffEvents");
  return { title: t("newTitle") };
}

export default function NewEventPage() {
  const t = useTranslations("StaffEvents");

  return (
    <div className="grid gap-4">
      <Link
        href="/staff/events"
        className="inline-flex items-center gap-1 justify-self-start text-sm text-muted-foreground underline-offset-4 hover:underline"
      >
        <ArrowLeft aria-hidden className="size-4" />
        {t("back")}
      </Link>
      <div>
        <h1 className="text-xl font-semibold">{t("newTitle")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{t("newIntro")}</p>
      </div>
      <StaffEventForm />
    </div>
  );
}
