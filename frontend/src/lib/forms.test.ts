import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api";
import { applyServerErrors } from "@/lib/forms";

describe("applyServerErrors", () => {
  it("puts backend validation errors on the matching fields and focuses the first", () => {
    const setError = vi.fn();
    const error = new ApiError({
      status: 400,
      code: "VALIDATION_FAILED",
      message: "Some fields are invalid.",
      errors: [
        { field: "email", code: "Email", message: "must be a well-formed email address" },
        { field: "phone", code: "Pattern", message: "invalid phone number" },
      ],
    });

    expect(applyServerErrors(error, setError)).toBe(true);
    expect(setError).toHaveBeenCalledWith(
      "email",
      { type: "server", message: "must be a well-formed email address" },
      { shouldFocus: true },
    );
    expect(setError).toHaveBeenCalledWith(
      "phone",
      { type: "server", message: "invalid phone number" },
      { shouldFocus: false },
    );
  });

  it("ignores errors that aren't validation errors", () => {
    const setError = vi.fn();
    const notFound = new ApiError({ status: 404, code: "BOOK_NOT_FOUND", message: "x" });

    expect(applyServerErrors(notFound, setError)).toBe(false);
    expect(applyServerErrors(new Error("boom"), setError)).toBe(false);
    expect(setError).not.toHaveBeenCalled();
  });
});
