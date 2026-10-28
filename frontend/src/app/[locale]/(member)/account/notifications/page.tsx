import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { NotificationsPage } from "@/features/notifications/notifications-page";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Notifications");
  return { title: t("metaTitle") };
}

export default function MyNotificationsPage() {
  const t = useTranslations("Notifications");

  return (
    <div className="grid gap-6">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <NotificationsPage />
    </div>
  );
}
