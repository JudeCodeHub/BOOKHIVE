import { useTranslations } from "next-intl";

export default function StaffDashboard() {
  const t = useTranslations("StaffHome");

  return (
    <>
      <h1 className="text-xl font-semibold">{t("title")}</h1>
      <p className="mt-1 text-sm text-muted-foreground">{t("description")}</p>
    </>
  );
}
