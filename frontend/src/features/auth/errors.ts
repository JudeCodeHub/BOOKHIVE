"use client";

import { useTranslations } from "next-intl";
import { ApiError } from "@/lib/api";

/**
 * Turns an error from the API into a sentence in the user's language, chosen by the error's
 * stable code (INVALID_CREDENTIALS, TOO_MANY_REQUESTS...), never by the backend's English text.
 */
export function useAuthErrorMessage() {
  const t = useTranslations("Auth.errors");

  return (error: unknown): string => {
    const code = error instanceof ApiError ? error.code : undefined;
    // Codes come from the server as plain strings, so the typed key check is bypassed on purpose
    return code && t.has(code as never) ? t(code as never) : t("generic");
  };
}
