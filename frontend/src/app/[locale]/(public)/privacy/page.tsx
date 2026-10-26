import type { Metadata } from "next";
import { getLocale } from "next-intl/server";
import { PRIVACY, type Locale } from "@/content/legal";
import { LegalPage } from "@/features/library/legal-page";

export async function generateMetadata(): Promise<Metadata> {
  const locale = (await getLocale()) as Locale;
  return { title: PRIVACY[locale].title, description: PRIVACY[locale].intro.slice(0, 160) };
}

export default function PrivacyPage() {
  return <LegalPage documents={PRIVACY} />;
}
