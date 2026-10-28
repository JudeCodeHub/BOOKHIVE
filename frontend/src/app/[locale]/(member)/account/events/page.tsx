import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { MyEvents } from "@/features/events/my-events";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("MyEvents");
  return { title: t("metaTitle") };
}

export default function MyEventsPage() {
  const t = useTranslations("MyEvents");

  return (
    <div className="grid gap-6">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <MyEvents />
    </div>
  );
}
