import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "@/lib/api";
import {
  clearSession,
  getSession,
  hasRole,
  login,
  logout,
  refreshSession,
  SESSION_HINT_COOKIE,
  subscribe,
  type AuthResponse,
} from "@/features/auth/session";

const response: AuthResponse = {
  accessToken: "token-1",
  tokenType: "Bearer",
  expiresAt: "2026-10-23T10:15:00Z",
  user: {
    id: "7b0c6a3e-1d2f-4c1a-9a51-2f6c1c0b9e11",
    email: "nimali@mail.lk",
    fullName: "Nimali Perera",
    roles: ["MEMBER"],
    preferredLanguage: "si",
  },
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("session", () => {
  beforeEach(() => clearSession());
  afterEach(() => clearSession());

  it("login stores the session, sets the hint cookie and notifies listeners", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(response)),
    );
    const listener = vi.fn();
    const unsubscribe = subscribe(listener);

    await login("nimali@mail.lk", "reading-is-fun");

    expect(getSession()?.user.email).toBe("nimali@mail.lk");
    expect(document.cookie).toContain(`${SESSION_HINT_COOKIE}=MEMBER`);
    expect(listener).toHaveBeenCalled();
    unsubscribe();
  });

  it("sends the in-memory access token with later API calls", async () => {
    const fetchMock = vi.fn(async () => jsonResponse(response));
    vi.stubGlobal("fetch", fetchMock);
    await login("nimali@mail.lk", "reading-is-fun");

    await api.get("/me");

    const init = (fetchMock.mock.calls[1] as unknown[])[1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get("Authorization")).toBe("Bearer token-1");
  });

  it("runs only one refresh at a time, so the refresh token is never sent twice", async () => {
    let finish: (value: Response) => void = () => {};
    const fetchMock = vi.fn(() => new Promise<Response>((resolve) => (finish = resolve)));
    vi.stubGlobal("fetch", fetchMock);

    const first = refreshSession();
    const second = refreshSession();
    finish(jsonResponse({ ...response, accessToken: "token-2" }));

    expect(await first).toEqual(await second);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getSession()?.accessToken).toBe("token-2");
  });

  it("a refused refresh means logged out, not an error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(response)),
    );
    await login("nimali@mail.lk", "reading-is-fun");
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse({ code: "INVALID_REFRESH_TOKEN" }, 401)),
    );

    await expect(refreshSession()).resolves.toBeNull();
    expect(getSession()).toBeNull();
    expect(document.cookie).not.toContain(`${SESSION_HINT_COOKIE}=MEMBER`);
  });

  it("a network error during refresh keeps the session and reports the error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(response)),
    );
    await login("nimali@mail.lk", "reading-is-fun");
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Promise.reject(new TypeError("offline"))),
    );

    await expect(refreshSession()).rejects.toBeInstanceOf(ApiError);
    expect(getSession()).not.toBeNull();
  });

  it("logout clears the session even if the server can't be reached", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => jsonResponse(response)),
    );
    await login("nimali@mail.lk", "reading-is-fun");
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => Promise.reject(new TypeError("offline"))),
    );

    await logout();

    expect(getSession()).toBeNull();
  });

  it("an admin counts as a librarian, like on the backend", () => {
    const admin = { ...response, user: { ...response.user, roles: ["ADMIN" as const] } };

    expect(hasRole({ ...admin, user: admin.user }, "LIBRARIAN")).toBe(true);
    expect(hasRole({ ...response, user: response.user }, "LIBRARIAN")).toBe(false);
    expect(hasRole(null, "MEMBER")).toBe(false);
  });
});
