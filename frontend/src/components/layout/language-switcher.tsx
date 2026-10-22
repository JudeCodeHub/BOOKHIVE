import { useLocale, useTranslations } from "next-intl";
import { cn } from "@/lib/utils";

// Each language is always shown in its own script, so these names are not translated
const LANGUAGES = [
  { code: "en", label: "EN", name: "English" },
  { code: "si", label: "සිං", name: "සිංහල" },
  { code: "ta", label: "தமி", name: "தமிழ்" },
] as const;

/**
 * English / Sinhala / Tamil switcher. Display only for now: clicking to switch comes in
 * checklist step 78.
 */
export function LanguageSwitcher() {
  const t = useTranslations("Header");
  const current = useLocale();

  return (
    <div role="group" aria-label={t("language")} className="flex rounded-md border p-0.5 text-sm">
      {LANGUAGES.map((lang) => (
        <span
          key={lang.code}
          lang={lang.code}
          title={lang.name}
          aria-current={lang.code === current ? "true" : undefined}
          className={cn(
            "rounded-sm px-2 py-1",
            lang.code === current
              ? "bg-primary font-medium text-primary-foreground"
              : "text-muted-foreground",
          )}
        >
          {lang.label}
        </span>
      ))}
    </div>
  );
}
