import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { NotificationSettings } from "@/features/notifications/notification-settings";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("NotificationSettings");
  return { title: t("metaTitle") };
}

export default function NotificationSettingsPage() {
  const t = useTranslations("NotificationSettings");

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">{t("title")}</h1>
        <p className="mt-1 text-muted-foreground">{t("intro")}</p>
      </div>
      <NotificationSettings />
    </div>
  );
}
