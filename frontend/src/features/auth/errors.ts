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

/**
 * Like useAuthErrorMessage, but a page's own `errors.<CODE>` texts come first (e.g. "Sign-up for
 * this event has closed." in EventPage.errors); codes the page doesn't have fall back to the
 * shared ones above.
 *
 *   const errorText = useErrorMessage("EventPage");
 *   onError: (error) => toast.error(errorText(error))
 */
export function useErrorMessage(namespace: string) {
  // The namespace is chosen by the caller, so the typed key check can't help here either
  const t = useTranslations(namespace as never) as unknown as {
    (key: string): string;
    has: (key: string) => boolean;
  };
  const shared = useAuthErrorMessage();

  return (error: unknown): string => {
    const code = error instanceof ApiError ? error.code : undefined;
    return code && t.has(`errors.${code}`) ? t(`errors.${code}`) : shared(error);
  };
}
