import type messages from "../../messages/en.json";
import type { routing } from "@/i18n/routing";

// Makes next-intl type-safe: t("Home.taglne") is a compile error, and locales are "en"|"si"|"ta".
// English is the source of truth; si.json and ta.json must have the same keys.
declare module "next-intl" {
  interface AppConfig {
    Locale: (typeof routing.locales)[number];
    Messages: typeof messages;
  }
}
