import { hasLocale } from "next-intl";
import { getRequestConfig } from "next-intl/server";
import { notFound } from "next/navigation";
import * as rootParams from "next/root-params";
import { routing } from "@/i18n/routing";

/**
 * Runs for every server request: picks the language from the URL (/si/... -> "si") and loads
 * that language's messages. Unknown languages (/xx/...) are a 404.
 */
export default getRequestConfig(async ({ locale }) => {
  if (!locale) {
    const fromUrl = await rootParams.locale();
    if (!hasLocale(routing.locales, fromUrl)) {
      notFound();
    }
    locale = fromUrl;
  }

  return {
    locale,
    messages: (await import(`../../messages/${locale}.json`)).default,
    // Sri Lanka's time zone, so dates render the same on the server and in the browser
    timeZone: "Asia/Colombo",
  };
});
