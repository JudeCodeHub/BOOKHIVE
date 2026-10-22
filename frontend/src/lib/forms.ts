import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { ApiError } from "@/lib/api";

/**
 * Puts the backend's VALIDATION_FAILED field errors onto the matching form fields, so the
 * server's message shows under the input just like a client-side Zod error.
 *
 *   onError: (error) => {
 *     if (!applyServerErrors(error, form.setError)) toast.error(error.message);
 *   }
 *
 * Returns true if the error was a validation error and was applied.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
): boolean {
  if (!(error instanceof ApiError) || !error.isValidation) {
    return false;
  }
  error.errors.forEach((fieldError, index) => {
    setError(
      fieldError.field as Path<T>,
      { type: "server", message: fieldError.message },
      // Move focus to the first invalid field
      { shouldFocus: index === 0 },
    );
  });
  return true;
}
