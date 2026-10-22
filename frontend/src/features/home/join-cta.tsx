import { useTranslations } from "next-intl";
import { LogoMark } from "@/components/brand/logo";
import { Button } from "@/components/ui/button";
import { Link } from "@/i18n/navigation";

/** Invites visitors to apply for membership (PRD flow 1: Join). */
export function JoinCta() {
  const t = useTranslations("Home");

  return (
    <section aria-labelledby="join-title" className="mx-auto w-full max-w-6xl px-4 py-14">
      <div className="flex flex-col items-start gap-4 rounded-2xl bg-secondary p-8 text-secondary-foreground sm:flex-row sm:items-center sm:p-10">
        <LogoMark className="size-14" />
        <div className="flex-1">
          <h2 id="join-title" className="text-2xl font-semibold">
            {t("joinTitle")}
          </h2>
          <p className="mt-1 max-w-xl text-secondary-foreground/80">{t("joinText")}</p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Button asChild size="lg">
            <Link href="/join">{t("joinButton")}</Link>
          </Button>
          <Button
            asChild
            size="lg"
            variant="outline"
            className="border-white/30 bg-transparent text-secondary-foreground hover:bg-white/10 hover:text-secondary-foreground"
          >
            <Link href="/rules">{t("rulesButton")}</Link>
          </Button>
        </div>
      </div>
    </section>
  );
}
