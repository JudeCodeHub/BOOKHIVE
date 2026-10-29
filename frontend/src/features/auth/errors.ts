"use client";

import type { FieldValues, UseFormSetError } from "react-hook-form";
import { useTranslations } from "next-intl";
import { ApiError, type ValidationError } from "@/lib/api";
import { applyServerErrors } from "@/lib/forms";

/** The server's codes are plain strings, so the typed key check can't be used on them. */
type Lookup = { (key: string): string; has: (key: string) => boolean };

/**
 * Turns an error from the API into a sentence in the user's language, chosen by the error's
 * stable code (INVALID_CREDENTIALS, TOO_MANY_REQUESTS...), never by the backend's English text.
 * Login and account codes come from Auth.errors; everything else from the shared Errors list
 * (checklist step 381). Codes neither list knows fall back to "something went wrong".
 */
export function useAuthErrorMessage() {
  const auth = useTranslations("Auth.errors") as unknown as Lookup;
  const shared = useTranslations("Errors") as unknown as Lookup;

  return (error: unknown): string => {
    const code = error instanceof ApiError ? error.code : undefined;
    if (!code) return auth("generic");
    if (auth.has(code)) return auth(code);
    return shared.has(code) ? shared(code) : auth("generic");
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
  const t = useTranslations(namespace as never) as unknown as Lookup;
  const shared = useAuthErrorMessage();

  return (error: unknown): string => {
    const code = error instanceof ApiError ? error.code : undefined;
    return code && t.has(`errors.${code}`) ? t(`errors.${code}`) : shared(error);
  };
}

/**
 * Puts the server's field errors under their inputs, in the user's language (checklist step 381):
 * a page's own `translate` wins, then the shared Errors list by code, and only then the server's
 * own English sentence.
 *
 *   const applyFieldErrors = useFieldErrors();
 *   if (!applyFieldErrors(error, form.setError)) setFormError(errorMessage(error));
 *
 * @returns true when the error was a validation error and was shown on the fields
 */
export function useFieldErrors() {
  const shared = useTranslations("Errors") as unknown as Lookup;

  return <T extends FieldValues>(
    error: unknown,
    setError: UseFormSetError<T>,
    translate?: (fieldError: ValidationError) => string | undefined,
  ): boolean =>
    applyServerErrors(error, setError, (fieldError) => {
      const own = translate?.(fieldError);
      if (own) return own;
      return shared.has(fieldError.code) ? shared(fieldError.code) : undefined;
    });
}
