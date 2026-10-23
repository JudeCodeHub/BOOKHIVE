/**
 * The login session in the browser (ADR 0002). Holds the access token in memory only, refreshes
 * it through the httpOnly cookie, and tells other tabs about logouts.
 *
 *   await login(email, password);   // after this, api.* calls send the Bearer token
 *   await refreshSession();         // on app start: restores the session after a reload
 *   await logout();
 *
 * React components read it through the useSession hook (checklist step 112).
 */
import { ApiError, api, setAccessTokenProvider, type Schema } from "@/lib/api";

export type AuthResponse = Schema<"AuthResponse">;
export type SessionUser = Schema<"UserSummary">;
export type Role = SessionUser["roles"][number];

export type Session = {
  accessToken: string;
  /** ISO time when the access token stops working */
  expiresAt: string;
  user: SessionUser;
};

/** Frontend-domain cookie meaning "this browser probably has a session" (no secret inside). */
export const SESSION_HINT_COOKIE = "bookhive_session";
const HINT_MAX_AGE_SECONDS = 7 * 24 * 60 * 60; // as long as the refresh token
const CHANNEL_NAME = "bookhive-auth";

let current: Session | null = null;
let refreshing: Promise<Session | null> | null = null;
const listeners = new Set<() => void>();

// Every API call made through lib/api.ts now carries the current access token
setAccessTokenProvider(() => current?.accessToken);

// --- Reading -----------------------------------------------------------------------------

export function getSession(): Session | null {
  return current;
}

/** For useSyncExternalStore: call `listener` whenever the session changes. */
export function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

// --- Changing ----------------------------------------------------------------------------

/** Email + password. Throws ApiError (INVALID_CREDENTIALS, EMAIL_NOT_VERIFIED...) on failure. */
export async function login(email: string, password: string): Promise<Session> {
  const response = await api.post<AuthResponse>("/auth/login", { email, password });
  return setSession(fromResponse(response));
}

/**
 * Gets a new access token using the refresh cookie. Returns null if there's no valid session.
 *
 * Single flight: callers arriving while a refresh is running share it. Two parallel refreshes
 * would send the same refresh token twice, which the backend treats as theft (reuse detection)
 * and logs the user out everywhere.
 */
export function refreshSession(): Promise<Session | null> {
  refreshing ??= doRefresh().finally(() => {
    refreshing = null;
  });
  return refreshing;
}

async function doRefresh(): Promise<Session | null> {
  try {
    const response = await api.post<AuthResponse>("/auth/refresh");
    return setSession(fromResponse(response));
  } catch (error) {
    // 401 = no session (expired, logged out, revoked): that's an answer, not an error
    if (error instanceof ApiError && error.status === 401) {
      clearSession();
      return null;
    }
    // Network trouble etc.: keep whatever we have and let the caller decide
    throw error;
  }
}

/** Ends the session on the server and in every open tab. Never fails from the user's view. */
export async function logout(): Promise<void> {
  try {
    await api.post("/auth/logout");
  } catch {
    // Even if the server can't be reached, the user wants to be logged out here
  } finally {
    clearSession();
    broadcast({ type: "logout" });
  }
}

/** Also used after a password change, which returns a fresh session (POST /me/password). */
export function setSession(session: Session): Session {
  current = session;
  writeHintCookie(true);
  notify();
  return session;
}

export function clearSession(): void {
  if (current === null && !hasHintCookie()) {
    return;
  }
  current = null;
  writeHintCookie(false);
  notify();
}

export function fromResponse(response: AuthResponse): Session {
  return { accessToken: response.accessToken, expiresAt: response.expiresAt, user: response.user };
}

// --- Helpers -----------------------------------------------------------------------------

export function hasRole(session: Session | null, role: Role): boolean {
  if (!session) return false;
  const roles = session.user.roles;
  // An admin can do everything a librarian can (same rule as the backend's role hierarchy)
  return roles.includes(role) || (role === "LIBRARIAN" && roles.includes("ADMIN"));
}

function notify() {
  listeners.forEach((listener) => listener());
}

function writeHintCookie(loggedIn: boolean) {
  if (typeof document === "undefined") return;
  document.cookie = loggedIn
    ? `${SESSION_HINT_COOKIE}=1; Path=/; Max-Age=${HINT_MAX_AGE_SECONDS}; SameSite=Lax`
    : `${SESSION_HINT_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
}

function hasHintCookie(): boolean {
  return typeof document !== "undefined" && document.cookie.includes(`${SESSION_HINT_COOKIE}=1`);
}

// --- Other tabs ----------------------------------------------------------------------------

type AuthMessage = { type: "logout" };

const channel = typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel(CHANNEL_NAME);

channel?.addEventListener("message", (event: MessageEvent<AuthMessage>) => {
  if (event.data?.type === "logout") {
    clearSession();
  }
});

function broadcast(message: AuthMessage) {
  channel?.postMessage(message);
}
