import { Suspense } from "react";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { DeskMode } from "@/features/desk/desk-mode";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Desk");
  return { title: t("metaTitle") };
}

export default function DeskPage() {
  const t = useTranslations("Desk");

  return (
    <div className="grid gap-4">
      <h1 className="text-xl font-semibold">{t("title")}</h1>
      {/* Reads ?mode= from the URL, which is only known in the browser */}
      <Suspense>
        <DeskMode />
      </Suspense>
    </div>
  );
}
