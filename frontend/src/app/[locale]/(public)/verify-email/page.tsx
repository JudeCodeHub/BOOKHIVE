import { Suspense } from "react";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { PageLoader } from "@/components/feedback/page-loader";
import { AuthCard } from "@/features/auth/auth-card";
import { VerifyEmail } from "@/features/auth/verify-email";

export async function generateMetadata(): Promise<Metadata> {
  // The token is in the URL: keep this page out of search engines
  const t = await getTranslations("Auth.verify");
  return { title: t("metaTitle"), robots: { index: false } };
}

export default function VerifyEmailPage() {
  const t = useTranslations("Auth.verify");

  return (
    <AuthCard title={t("metaTitle")}>
      {/* Reads ?token= from the URL, which is only known in the browser */}
      <Suspense fallback={<PageLoader />}>
        <VerifyEmail />
      </Suspense>
    </AuthCard>
  );
}
