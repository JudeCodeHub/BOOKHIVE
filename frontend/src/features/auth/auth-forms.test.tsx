import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LoginForm } from "@/features/auth/login-form";
import { RegisterForm } from "@/features/auth/register-form";
import { clearSession } from "@/features/auth/session";
import { renderWithIntl } from "@/test/render";

// Navigation needs the Next.js router, which doesn't exist in a unit test
const replace = vi.fn();
vi.mock("@/i18n/navigation", () => ({
  useRouter: () => ({ replace }),
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams("next=/account/loans"),
}));

function respond(status: number, body: unknown) {
  return vi.fn(
    async () =>
      new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
      }),
  );
}

describe("LoginForm", () => {
  beforeEach(() => replace.mockReset());
  afterEach(() => clearSession());

  it("logs in and continues to the page the user wanted, in their language", async () => {
    vi.stubGlobal(
      "fetch",
      respond(200, {
        accessToken: "token",
        tokenType: "Bearer",
        expiresAt: "2026-10-24T10:15:00Z",
        user: {
          id: "1",
          email: "nimali@mail.lk",
          fullName: "Nimali",
          roles: ["MEMBER"],
          preferredLanguage: "si",
        },
      }),
    );
    renderWithIntl(<LoginForm />);

    await userEvent.type(screen.getByLabelText(/Email/), "nimali@mail.lk");
    await userEvent.type(screen.getByLabelText(/Password/), "reading-is-fun");
    await userEvent.click(screen.getByRole("button", { name: "Log in" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/account/loans", { locale: "si" }));
  });

  it("shows the server's answer as a translated sentence", async () => {
    vi.stubGlobal("fetch", respond(401, { code: "INVALID_CREDENTIALS", detail: "ignored" }));
    renderWithIntl(<LoginForm />);

    await userEvent.type(screen.getByLabelText(/Email/), "nimali@mail.lk");
    await userEvent.type(screen.getByLabelText(/Password/), "wrong");
    await userEvent.click(screen.getByRole("button", { name: "Log in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The email or password is incorrect.",
    );
    expect(screen.getByLabelText(/Password/)).toHaveValue(""); // cleared for the next try
    expect(replace).not.toHaveBeenCalled();
  });
});

describe("RegisterForm", () => {
  it("checks the input before sending anything", async () => {
    const fetchMock = respond(201, {});
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<RegisterForm />);

    await userEvent.type(screen.getByLabelText(/Email/), "not-an-email");
    await userEvent.type(screen.getByLabelText(/Password/), "short");
    await userEvent.type(screen.getByLabelText(/Mobile number/), "12345");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Please enter a valid email address.")).toBeInTheDocument();
    expect(screen.getByText("Use at least 8 characters.")).toBeInTheDocument();
    expect(screen.getByText("Enter a Sri Lankan number like 0771234567.")).toBeInTheDocument();
    expect(screen.getByText("Please fill this in.")).toBeInTheDocument(); // full name
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows a server-side password problem under the password field", async () => {
    vi.stubGlobal(
      "fetch",
      respond(400, {
        code: "VALIDATION_FAILED",
        errors: [{ field: "password", code: "PASSWORD_TOO_COMMON", message: "English text" }],
      }),
    );
    renderWithIntl(<RegisterForm />);

    await userEvent.type(screen.getByLabelText(/Full name/), "Nimali Perera");
    await userEvent.type(screen.getByLabelText(/Email/), "nimali@mail.lk");
    await userEvent.type(screen.getByLabelText(/Password/), "password123");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));

    const password = screen.getByLabelText(/Password/);
    await waitFor(() => expect(password).toHaveAttribute("aria-invalid", "true"));
    expect(password).toHaveAccessibleDescription(
      expect.stringContaining("This password is too easy to guess."),
    );
  });

  it("asks the user to check their email after registering", async () => {
    const fetchMock = respond(201, {
      id: "1",
      email: "nimali@mail.lk",
      status: "PENDING_VERIFICATION",
    });
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<RegisterForm />);

    await userEvent.type(screen.getByLabelText(/Full name/), "Nimali Perera");
    await userEvent.type(screen.getByLabelText(/Email/), "nimali@mail.lk");
    await userEvent.type(screen.getByLabelText(/Password/), "reading-is-fun");
    await userEvent.type(screen.getByLabelText(/Mobile number/), "077 123 4567");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByText("Check your email")).toBeInTheDocument();
    const request = (fetchMock.mock.calls[0] as unknown[])[1] as RequestInit;
    const sent = JSON.parse(request.body as string);
    expect(sent).toMatchObject({ phone: "0771234567", preferredLanguage: "en" }); // spaces removed
  });
});
