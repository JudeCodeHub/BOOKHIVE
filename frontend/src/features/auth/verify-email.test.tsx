import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { VerifyEmail } from "@/features/auth/verify-email";
import { renderWithIntl } from "@/test/render";

let search = "token=abc";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(search) }));
vi.mock("@/i18n/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

function renderPage(fetchMock: ReturnType<typeof vi.fn>) {
  vi.stubGlobal("fetch", fetchMock);
  const client = new QueryClient();
  return renderWithIntl(
    <QueryClientProvider client={client}>
      <VerifyEmail />
    </QueryClientProvider>,
  );
}

const status = (code: number, body?: unknown) =>
  vi.fn(
    async () =>
      new Response(body === undefined ? null : JSON.stringify(body), {
        status: code,
        headers: { "Content-Type": "application/json" },
      }),
  );

describe("VerifyEmail", () => {
  it("shows success for a 204 answer (no body)", async () => {
    search = "token=abc";
    renderPage(status(204));

    expect(await screen.findByText("Email confirmed")).toBeInTheDocument();
  });

  it("offers a new link when the link expired", async () => {
    search = "token=abc";
    renderPage(status(422, { code: "VERIFICATION_TOKEN_EXPIRED" }));

    expect(await screen.findByText("This link has expired")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Get a new link" })).toHaveAttribute(
      "href",
      "/forgot-password",
    );
  });

  it("explains a used or unknown link", async () => {
    search = "token=abc";
    renderPage(status(422, { code: "VERIFICATION_TOKEN_INVALID" }));

    expect(await screen.findByText("This link doesn't work")).toBeInTheDocument();
  });

  it("doesn't call the server without a token", async () => {
    search = "";
    const fetchMock = status(204);
    renderPage(fetchMock);

    expect(await screen.findByText("No verification link")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
