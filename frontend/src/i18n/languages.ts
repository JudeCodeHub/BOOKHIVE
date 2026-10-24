import type { Locale } from "@/i18n/routing";

// Each language is always shown in its own script, so these names are not translated
export const LANGUAGES: { code: Locale; label: string; name: string }[] = [
  { code: "en", label: "EN", name: "English" },
  { code: "si", label: "සිං", name: "සිංහල" },
  { code: "ta", label: "தமி", name: "தமிழ்" },
];
