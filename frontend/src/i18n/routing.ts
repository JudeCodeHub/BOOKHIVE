import { defineRouting } from "next-intl/routing";

/** BookHive's languages (PRD I18N-1). Every URL starts with one: /en, /si, /ta. */
export const routing = defineRouting({
  locales: ["en", "si", "ta"],
  defaultLocale: "en",
});

export type Locale = (typeof routing.locales)[number];
