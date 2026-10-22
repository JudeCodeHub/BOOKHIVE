import { cn } from "@/lib/utils";

const LANGUAGES = [
  { code: "en", label: "EN", name: "English" },
  { code: "si", label: "සිං", name: "සිංහල" },
  { code: "ta", label: "தமி", name: "தமிழ்" },
] as const;

/**
 * English / Sinhala / Tamil switcher. Display only for now: switching starts working once
 * next-intl adds the /en, /si, /ta routes (checklist step 70 and 78).
 */
export function LanguageSwitcher({ current = "en" }: { current?: string }) {
  return (
    <div role="group" aria-label="Language" className="flex rounded-md border p-0.5 text-sm">
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
