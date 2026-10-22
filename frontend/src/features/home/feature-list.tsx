import { BookMarked, RefreshCw, Search, Wallet } from "lucide-react";
import { useTranslations } from "next-intl";

// What members can do online (PRD §1.2), in the order people need them
const FEATURES = [
  { key: "availability", icon: Search },
  { key: "reserve", icon: BookMarked },
  { key: "renew", icon: RefreshCw },
  { key: "pay", icon: Wallet },
] as const;

export function FeatureList() {
  const t = useTranslations("Home");

  return (
    <section aria-labelledby="features-title" className="border-y bg-muted/50">
      <div className="mx-auto max-w-6xl px-4 py-14">
        <h2 id="features-title" className="text-2xl font-semibold sm:text-3xl">
          {t("featuresTitle")}
        </h2>
        <ul className="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map(({ key, icon: Icon }) => (
            <li key={key} className="flex flex-col gap-2">
              <span className="flex size-11 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <Icon aria-hidden className="size-5" />
              </span>
              <h3 className="font-sans text-lg font-semibold">{t(`features.${key}.title`)}</h3>
              <p className="text-muted-foreground">{t(`features.${key}.text`)}</p>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
