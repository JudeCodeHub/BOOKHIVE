import { useTranslations } from "next-intl";
import { LogoMark } from "@/components/brand/logo";
import { Button } from "@/components/ui/button";
import { Link } from "@/i18n/navigation";

export default function NotFound() {
  const t = useTranslations("NotFound");

  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center">
      <LogoMark className="size-14" />
      <p className="font-heading text-5xl font-semibold text-primary">404</p>
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <p className="max-w-md text-muted-foreground">{t("description")}</p>
      <Button asChild className="mt-2">
        <Link href="/">{t("home")}</Link>
      </Button>
    </main>
  );
}
