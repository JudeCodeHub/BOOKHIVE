import { useTranslations } from "next-intl";

export default function AccountDashboard() {
  const t = useTranslations("AccountHome");

  return (
    <>
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <p className="mt-2 text-muted-foreground">{t("description")}</p>
    </>
  );
}
