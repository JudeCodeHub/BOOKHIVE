import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { ApiError, type ValidationError } from "@/lib/api";

/**
 * Puts the backend's VALIDATION_FAILED field errors onto the matching form fields, so the
 * server's message shows under the input just like a client-side Zod error.
 *
 *   onError: (error) => {
 *     if (!applyServerErrors(error, form.setError, translate)) toast.error(...);
 *   }
 *
 * @param translate optional: a message in the user's language for an error code (e.g.
 *   PASSWORD_TOO_COMMON); without one, the backend's English message is shown
 * @returns true if the error was a validation error and was applied
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  translate?: (fieldError: ValidationError) => string | undefined,
): boolean {
  if (!(error instanceof ApiError) || !error.isValidation) {
    return false;
  }
  error.errors.forEach((fieldError, index) => {
    setError(
      fieldError.field as Path<T>,
      { type: "server", message: translate?.(fieldError) ?? fieldError.message },
      // Move focus to the first invalid field
      { shouldFocus: index === 0 },
    );
  });
  return true;
}

/** Byte length in UTF-8: Sinhala and Tamil letters take 3 bytes each. */
export function utf8Length(text: string): number {
  return new TextEncoder().encode(text).length;
}
