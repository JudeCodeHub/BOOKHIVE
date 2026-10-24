import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ChangePasswordForm } from "@/features/account/change-password-form";
import { ProfileForm, type Profile } from "@/features/account/profile-form";
import { clearSession, getSession, setSession } from "@/features/auth/session";
import { renderWithIntl } from "@/test/render";

const replace = vi.fn();
vi.mock("@/i18n/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/account/settings",
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

const profile: Profile = {
  id: "1",
  email: "nimali@mail.lk",
  fullName: "Nimali Perera",
  phone: null,
  preferredLanguage: "en",
  roles: ["MEMBER"],
  status: "ACTIVE",
  emailVerifiedAt: "2026-10-20T10:00:00Z",
  createdAt: "2026-10-20T09:00:00Z",
};

/** A fake backend: answers each request in turn and records what was sent. */
function mockApi(...responses: Response[]) {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>();
  responses.forEach((response) => fetchMock.mockResolvedValueOnce(response));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function problem(status: number, body: object) {
  return Response.json(body, { status, headers: { "Content-Type": "application/problem+json" } });
}

function sentBody(fetchMock: ReturnType<typeof mockApi>, call: number) {
  return JSON.parse(String(fetchMock.mock.calls[call][1]?.body));
}

function renderWithQuery(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithIntl(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  replace.mockReset();
  setSession({
    accessToken: "token",
    expiresAt: "2099-01-01T00:00:00Z",
    user: {
      id: "1",
      email: profile.email,
      fullName: profile.fullName,
      roles: ["MEMBER"],
      preferredLanguage: "en",
    },
  });
});
afterEach(() => clearSession());

describe("ProfileForm", () => {
  it("loads the profile, saves the changes and updates the header name", async () => {
    const fetchMock = mockApi(
      Response.json(profile),
      Response.json({ ...profile, fullName: "Nimali Silva", phone: "0771234567" }),
    );
    renderWithQuery(<ProfileForm />);

    const name = await screen.findByLabelText(/Full name/);
    expect(screen.getByLabelText("Email")).toHaveValue("nimali@mail.lk");
    await userEvent.clear(name);
    await userEvent.type(name, "Nimali Silva");
    await userEvent.type(screen.getByLabelText(/Mobile number/), "077 123 4567");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(getSession()?.user.fullName).toBe("Nimali Silva"));
    expect(fetchMock.mock.calls[1][1]?.method).toBe("PATCH");
    expect(sentBody(fetchMock, 1)).toEqual({
      fullName: "Nimali Silva",
      phone: "0771234567",
      preferredLanguage: "en",
    });
    // Same language: no need to change the URL
    expect(replace).not.toHaveBeenCalled();
  });

  it("switches the website to the newly chosen language", async () => {
    mockApi(Response.json(profile), Response.json({ ...profile, preferredLanguage: "ta" }));
    renderWithQuery(<ProfileForm />);

    await userEvent.click(await screen.findByRole("radio", { name: "தமிழ்" }));
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith("/account/settings", { locale: "ta" }),
    );
  });

  it("checks the phone number before sending", async () => {
    const fetchMock = mockApi(Response.json(profile));
    renderWithQuery(<ProfileForm />);

    await userEvent.type(await screen.findByLabelText(/Mobile number/), "12345");
    await userEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Enter a Sri Lankan number like 0771234567.")).toBeVisible();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe("ChangePasswordForm", () => {
  async function fillIn(current: string, next: string, confirm = next) {
    await userEvent.type(screen.getByLabelText(/Current password/), current);
    await userEvent.type(screen.getByLabelText(/^New password/), next);
    await userEvent.type(screen.getByLabelText(/Repeat the new password/), confirm);
    await userEvent.click(screen.getByRole("button", { name: "Change password" }));
  }

  it("changes the password and keeps the fresh session from the server", async () => {
    const fetchMock = mockApi(
      Response.json({
        accessToken: "token-after-change",
        tokenType: "Bearer",
        expiresAt: "2099-01-01T00:00:00Z",
        user: {
          id: "1",
          email: profile.email,
          fullName: profile.fullName,
          roles: ["MEMBER"],
          preferredLanguage: "en",
        },
      }),
    );
    renderWithIntl(<ChangePasswordForm />);

    await fillIn("old-password-1", "a new reading list");

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/account/settings"));
    expect(sentBody(fetchMock, 0)).toEqual({
      currentPassword: "old-password-1",
      newPassword: "a new reading list",
    });
    expect(getSession()?.accessToken).toBe("token-after-change");
  });

  it("shows a wrong current password under that field", async () => {
    mockApi(problem(422, { code: "CURRENT_PASSWORD_INCORRECT" }));
    renderWithIntl(<ChangePasswordForm />);

    await fillIn("wrong-password", "a new reading list");

    expect(await screen.findByText("Your current password is incorrect.")).toBeVisible();
    expect(screen.getByLabelText(/Current password/)).toHaveAttribute("aria-invalid", "true");
    expect(replace).not.toHaveBeenCalled();
  });

  it("catches two different new passwords before sending", async () => {
    const fetchMock = mockApi();
    renderWithIntl(<ChangePasswordForm />);

    await fillIn("old-password-1", "a new reading list", "a new reading lust");

    expect(await screen.findByText("The two passwords don't match.")).toBeVisible();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
