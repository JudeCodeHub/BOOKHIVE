import type { Metadata } from "next";
import { KeyRound } from "lucide-react";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ProfileForm } from "@/features/account/profile-form";
import { Link } from "@/i18n/navigation";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Settings");
  return { title: t("metaTitle") };
}

/** /account/settings: profile now; notification and privacy settings join later (PRD §10). */
export default function SettingsPage() {
  const t = useTranslations("Settings");

  return (
    <div className="grid max-w-2xl gap-6">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>

      <Card>
        <CardHeader>
          <CardTitle>
            <h2>{t("profile.title")}</h2>
          </CardTitle>
          <CardDescription>{t("profile.description")}</CardDescription>
        </CardHeader>
        <CardContent>
          <ProfileForm />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>
            <h2>{t("password.title")}</h2>
          </CardTitle>
          <CardDescription>{t("password.description")}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild variant="secondary">
            <Link href="/account/settings/password">
              <KeyRound aria-hidden />
              {t("password.open")}
            </Link>
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
