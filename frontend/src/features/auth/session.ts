/**
 * The login session in the browser (ADR 0002). Holds the access token in memory only, refreshes
 * it through the httpOnly cookie, and tells other tabs about logouts.
 *
 *   await login(email, password);   // after this, api.* calls send the Bearer token
 *   await restoreSession();         // on app start: restores the session after a reload
 *   await logout();
 *
 * React components read it through useSession() (use-session.ts).
 */
import {
  encodeHint,
  SESSION_HINT_COOKIE,
  SESSION_HINT_MAX_AGE_SECONDS,
} from "@/features/auth/session-hint";
import {
  ApiError,
  api,
  setAccessTokenProvider,
  setUnauthorizedHandler,
  type Schema,
} from "@/lib/api";

export { SESSION_HINT_COOKIE };

export type AuthResponse = Schema<"AuthResponse">;
export type SessionUser = Schema<"UserSummary">;
export type Role = SessionUser["roles"][number];

export type Session = {
  accessToken: string;
  /** ISO time when the access token stops working */
  expiresAt: string;
  user: SessionUser;
};

/**
 * - loading: a session may exist (hint cookie) and is being restored from the refresh cookie
 * - authenticated: logged in, `session` is set
 * - anonymous: not logged in
 */
export type AuthState =
  | { status: "loading"; session: null }
  | { status: "authenticated"; session: Session }
  | { status: "anonymous"; session: null };

const LOADING: AuthState = { status: "loading", session: null };
const ANONYMOUS: AuthState = { status: "anonymous", session: null };
const CHANNEL_NAME = "bookhive-auth";

// Without the hint cookie there is nothing to restore: anonymous straight away
let state: AuthState = hasHintCookie() ? LOADING : ANONYMOUS;
let refreshing: Promise<Session | null> | null = null;
const listeners = new Set<() => void>();

// Every API call made through lib/api.ts carries the current access token...
setAccessTokenProvider(() => state.session?.accessToken);
// ...and an expired one is renewed once, then the request is retried (checklist step 113)
setUnauthorizedHandler(async () => (await refreshSession()) !== null);

// --- Reading -----------------------------------------------------------------------------

export function getAuthState(): AuthState {
  return state;
}

/**
 * What the server renders: it can't know the session (it lives in browser memory), so pages
 * start as "loading" and settle in the browser. Keeps server and browser HTML identical.
 */
export function getServerAuthState(): AuthState {
  return LOADING;
}

export function getSession(): Session | null {
  return state.session;
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
 * Called once when the app starts (SessionProvider): if this browser had a session, get a fresh
 * access token for it. Network trouble counts as "not logged in" for now; the next action retries.
 */
export async function restoreSession(): Promise<void> {
  if (state.status !== "loading") return;
  try {
    await refreshSession();
  } catch {
    setState(ANONYMOUS);
  }
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
  writeHintCookie(session.user.roles);
  setState({ status: "authenticated", session });
  return session;
}

export function clearSession(): void {
  writeHintCookie(null);
  if (state.status !== "anonymous") {
    setState(ANONYMOUS);
  }
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

function setState(next: AuthState) {
  state = next;
  listeners.forEach((listener) => listener());
}

function writeHintCookie(roles: readonly string[] | null) {
  if (typeof document === "undefined") return;
  document.cookie = roles
    ? `${SESSION_HINT_COOKIE}=${encodeHint(roles)}; Path=/; Max-Age=${SESSION_HINT_MAX_AGE_SECONDS}; SameSite=Lax`
    : `${SESSION_HINT_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
}

function hasHintCookie(): boolean {
  return (
    typeof document !== "undefined" &&
    document.cookie
      .split("; ")
      .some(
        (c) => c.startsWith(`${SESSION_HINT_COOKIE}=`) && c.length > SESSION_HINT_COOKIE.length + 1,
      )
  );
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
