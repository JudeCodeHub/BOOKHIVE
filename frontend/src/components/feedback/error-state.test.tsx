import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ErrorState } from "@/components/feedback/error-state";
import { ApiError } from "@/lib/api";
import { renderWithIntl } from "@/test/render";

describe("ErrorState", () => {
  it("shows a friendly message and the request id, never the raw error text", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const error = new ApiError({
      status: 500,
      code: "INTERNAL_ERROR",
      message: "NullPointerException at LoanService.java:42",
      requestId: "req-123",
    });

    renderWithIntl(<ErrorState error={error} retry={() => {}} />);

    expect(screen.getByRole("alert")).toHaveTextContent("Something went wrong");
    expect(screen.getByText("Reference: req-123")).toBeInTheDocument();
    expect(screen.queryByText(/NullPointerException/)).not.toBeInTheDocument();
  });

  it("retries when the user clicks Try again", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const retry = vi.fn();

    renderWithIntl(<ErrorState error={new Error("boom")} retry={retry} />);
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(retry).toHaveBeenCalledOnce();
  });
});
