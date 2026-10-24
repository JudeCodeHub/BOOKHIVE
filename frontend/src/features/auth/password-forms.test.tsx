import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ForgotPasswordForm } from "@/features/auth/forgot-password-form";
import { SetPasswordForm } from "@/features/auth/set-password-form";
import { renderWithIntl } from "@/test/render";

let search = "token=abc";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(search) }));
vi.mock("@/i18n/navigation", () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

function respond(status: number, body?: unknown) {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
    async () =>
      new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
      }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

async function choosePassword(password: string, confirm = password) {
  await userEvent.type(screen.getByLabelText(/^New password/), password);
  await userEvent.type(screen.getByLabelText(/Repeat the new password/), confirm);
  await userEvent.click(screen.getByRole("button", { name: "Save password" }));
}

describe("ForgotPasswordForm", () => {
  it("always says 'check your email', whether or not the account exists", async () => {
    const fetchMock = respond(204);
    renderWithIntl(<ForgotPasswordForm />);

    await userEvent.type(screen.getByLabelText(/Email/), "anyone@mail.lk");
    await userEvent.click(screen.getByRole("button", { name: "Send reset link" }));

    expect(await screen.findByText("Check your email")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});

describe("SetPasswordForm", () => {
  it("catches a mistyped repeat before sending", async () => {
    search = "token=abc";
    const fetchMock = respond(204);
    renderWithIntl(<SetPasswordForm mode="reset" />);

    await choosePassword("reading-is-fun", "reading-is-fan");

    expect(await screen.findByText("The two passwords don't match.")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("reset: sends the token and newPassword, then offers to log in", async () => {
    search = "token=abc";
    const fetchMock = respond(204);
    renderWithIntl(<SetPasswordForm mode="reset" />);

    await choosePassword("reading-is-fun");

    expect(await screen.findByText("Password changed")).toBeInTheDocument();
    const request = (fetchMock.mock.calls[0] as unknown[])[1] as RequestInit;
    expect(String((fetchMock.mock.calls[0] as unknown[])[0])).toContain("/auth/reset-password");
    expect(JSON.parse(request.body as string)).toEqual({
      token: "abc",
      newPassword: "reading-is-fun",
    });
  });

  it("invite: uses the accept-invite endpoint and its own success text", async () => {
    search = "token=xyz";
    const fetchMock = respond(204);
    renderWithIntl(<SetPasswordForm mode="invite" />);

    await choosePassword("reading-is-fun");

    expect(await screen.findByText("Your account is ready")).toBeInTheDocument();
    expect(String((fetchMock.mock.calls[0] as unknown[])[0])).toContain("/auth/accept-invite");
  });

  it("shows a too-common password under the password field, translated", async () => {
    search = "token=abc";
    respond(400, {
      code: "VALIDATION_FAILED",
      errors: [{ field: "newPassword", code: "PASSWORD_TOO_COMMON", message: "English" }],
    });
    renderWithIntl(<SetPasswordForm mode="reset" />);

    await choosePassword("password123");

    const password = screen.getByLabelText(/^New password/);
    await waitFor(() => expect(password).toHaveAttribute("aria-invalid", "true"));
    expect(password).toHaveAccessibleDescription(
      expect.stringContaining("This password is too easy to guess."),
    );
  });

  it("reset: an expired link offers a new one", async () => {
    search = "token=abc";
    respond(422, { code: "RESET_TOKEN_EXPIRED" });
    renderWithIntl(<SetPasswordForm mode="reset" />);

    await choosePassword("reading-is-fun");

    expect(await screen.findByText("This link has expired")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Get a new link" })).toHaveAttribute(
      "href",
      "/forgot-password",
    );
  });

  it("invite: an expired link tells the user to ask an admin", async () => {
    search = "token=abc";
    respond(422, { code: "INVITE_TOKEN_EXPIRED" });
    renderWithIntl(<SetPasswordForm mode="invite" />);

    await choosePassword("reading-is-fun");

    expect(
      await screen.findByText("Ask an administrator to send you a new invitation."),
    ).toBeInTheDocument();
  });

  it("explains a missing link without showing the form", () => {
    search = "";
    renderWithIntl(<SetPasswordForm mode="reset" />);

    expect(screen.getByText("No link found")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
