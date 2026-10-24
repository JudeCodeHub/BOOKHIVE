import type { Metadata } from "next";
import { ArrowLeft } from "lucide-react";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { Card, CardContent } from "@/components/ui/card";
import { ChangePasswordForm } from "@/features/account/change-password-form";
import { Link } from "@/i18n/navigation";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Settings.password");
  return { title: t("metaTitle") };
}

export default function ChangePasswordPage() {
  const t = useTranslations("Settings.password");

  return (
    <div className="grid max-w-md gap-4">
      <Link
        href="/account/settings"
        className="inline-flex items-center gap-1 justify-self-start text-sm text-muted-foreground underline-offset-4 hover:underline"
      >
        <ArrowLeft aria-hidden className="size-4" />
        {t("back")}
      </Link>
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <p className="text-muted-foreground">{t("description")}</p>
      <Card>
        <CardContent>
          <ChangePasswordForm />
        </CardContent>
      </Card>
    </div>
  );
}
