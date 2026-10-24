import { Suspense } from "react";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { PageLoader } from "@/components/feedback/page-loader";
import { AuthCard } from "@/features/auth/auth-card";
import { SetPasswordForm } from "@/features/auth/set-password-form";

export async function generateMetadata(): Promise<Metadata> {
  // The token is in the URL: keep this page out of search engines
  const t = await getTranslations("Auth.reset");
  return { title: t("metaTitle"), robots: { index: false } };
}

export default function ResetPasswordPage() {
  const t = useTranslations("Auth.reset");

  return (
    <AuthCard title={t("title")} subtitle={t("subtitle")}>
      {/* Reads ?token= from the URL, which is only known in the browser */}
      <Suspense fallback={<PageLoader />}>
        <SetPasswordForm mode="reset" />
      </Suspense>
    </AuthCard>
  );
}
