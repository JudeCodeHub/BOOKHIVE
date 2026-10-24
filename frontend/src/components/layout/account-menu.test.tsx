import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AccountMenu } from "@/components/layout/account-menu";
import { clearSession, getSession, login } from "@/features/auth/session";
import { renderWithIntl } from "@/test/render";

const replace = vi.fn();
vi.mock("@/i18n/navigation", () => ({
  useRouter: () => ({ replace }),
  Link: ({ href, children, ...props }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

/** Logs in as a user with these roles (the fake backend answers every request with the user). */
async function loginAs(roles: string[]) {
  const fetchMock = vi.fn<(url: string) => Promise<Response>>(async () =>
    Response.json({
      accessToken: "token",
      tokenType: "Bearer",
      expiresAt: "2026-10-24T10:15:00Z",
      user: {
        id: "1",
        email: "kamal@mail.lk",
        fullName: "Kamal Silva",
        roles,
        preferredLanguage: "en",
      },
    }),
  );
  vi.stubGlobal("fetch", fetchMock);
  await login("kamal@mail.lk", "reading-is-fun");
  return fetchMock;
}

async function openMenu() {
  await userEvent.click(screen.getByRole("button", { name: /Kamal/ }));
  return screen.findByRole("menu");
}

describe("AccountMenu", () => {
  beforeEach(() => replace.mockReset());
  afterEach(() => clearSession());

  it("offers visitors the login link", () => {
    renderWithIntl(<AccountMenu />);

    expect(screen.getByRole("link", { name: "Log in" })).toHaveAttribute("href", "/login");
  });

  it("shows a member only their own account, not the staff or admin consoles", async () => {
    await loginAs(["MEMBER"]);
    renderWithIntl(<AccountMenu />);

    const menu = await openMenu();

    expect(menu).toHaveTextContent("kamal@mail.lk");
    expect(screen.getByRole("menuitem", { name: "My account" })).toHaveAttribute(
      "href",
      "/account",
    );
    expect(screen.queryByRole("menuitem", { name: "Staff console" })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: "Admin console" })).toBeNull();
  });

  it("shows an admin every area", async () => {
    await loginAs(["LIBRARIAN", "ADMIN"]);
    renderWithIntl(<AccountMenu />);

    await openMenu();

    expect(screen.getAllByRole("menuitem").map((item) => item.textContent)).toEqual([
      "My account",
      "Staff console",
      "Admin console",
      "Log out",
    ]);
  });

  it("logs out on the server, forgets the session and goes to the home page", async () => {
    const fetchMock = await loginAs(["MEMBER"]);
    renderWithIntl(<AccountMenu />);

    await openMenu();
    await userEvent.click(screen.getByRole("menuitem", { name: "Log out" }));

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
    expect(String(fetchMock.mock.calls.at(-1)?.[0])).toMatch(/\/auth\/logout$/);
    expect(getSession()).toBeNull();
    expect(await screen.findByRole("link", { name: "Log in" })).toBeInTheDocument();
  });
});
