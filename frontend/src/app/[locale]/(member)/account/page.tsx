import { useTranslations } from "next-intl";
import { ReadyForPickupBanner } from "@/features/holds/ready-banner";

export default function AccountDashboard() {
  const t = useTranslations("AccountHome");

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">{t("title")}</h1>
        <p className="mt-2 text-muted-foreground">{t("description")}</p>
      </div>
      <ReadyForPickupBanner />
    </div>
  );
}
