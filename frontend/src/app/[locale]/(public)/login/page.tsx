import { Suspense } from "react";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { PageLoader } from "@/components/feedback/page-loader";
import { AuthCard } from "@/features/auth/auth-card";
import { LoginForm } from "@/features/auth/login-form";
import { Link } from "@/i18n/navigation";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Auth.login");
  return { title: t("metaTitle") };
}

export default function LoginPage() {
  const t = useTranslations("Auth.login");

  return (
    <AuthCard
      title={t("title")}
      subtitle={t("subtitle")}
      footer={
        <>
          {t("noAccount")}{" "}
          <Link
            href="/register"
            className="font-medium text-foreground underline-offset-4 hover:underline"
          >
            {t("register")}
          </Link>
        </>
      }
    >
      {/* The form reads ?next= from the URL, which is only known in the browser */}
      <Suspense fallback={<PageLoader />}>
        <LoginForm />
      </Suspense>
    </AuthCard>
  );
}
