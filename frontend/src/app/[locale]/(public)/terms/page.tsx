import type { Metadata } from "next";
import { getLocale } from "next-intl/server";
import { TERMS, type Locale } from "@/content/legal";
import { LegalPage } from "@/features/library/legal-page";

export async function generateMetadata(): Promise<Metadata> {
  const locale = (await getLocale()) as Locale;
  return { title: TERMS[locale].title, description: TERMS[locale].intro.slice(0, 160) };
}

export default function TermsPage() {
  return <LegalPage documents={TERMS} />;
}
