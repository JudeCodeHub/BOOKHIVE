import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { AuthCard } from "@/features/auth/auth-card";
import { RegisterForm } from "@/features/auth/register-form";
import { Link } from "@/i18n/navigation";

// Checked in development for instant navigation (next.config: manual-warning)
export const instant = true;

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Auth.register");
  return { title: t("metaTitle") };
}

export default function RegisterPage() {
  const t = useTranslations("Auth.register");

  return (
    <AuthCard
      title={t("title")}
      subtitle={t("subtitle")}
      footer={
        <>
          {t("haveAccount")}{" "}
          <Link
            href="/login"
            className="font-medium text-foreground underline-offset-4 hover:underline"
          >
            {t("login")}
          </Link>
        </>
      }
    >
      <RegisterForm />
    </AuthCard>
  );
}
