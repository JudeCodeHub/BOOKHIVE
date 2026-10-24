# 0002. How the frontend holds the login session

- Status: Accepted
- Date: 2026-10-23
- Builds on: [0001](0001-auth-tokens.md)
- Related: checklist steps 107, 112–116

## Context

ADR 0001 gives the browser two things after login: a 15-minute **access token** (response body) and a 7-day **refresh token** (httpOnly cookie, scoped to the backend's `/api/v1/auth`). The Next.js frontend has to:

- keep the access token somewhere JavaScript can use it, without exposing it to XSS more than necessary;
- get a new one before or when it expires, without logging the user out every 15 minutes;
- survive a page reload, and keep several browser tabs in agreement;
- let Next.js decide, **before** rendering, whether to send a visitor to the login page (`/account`, `/staff`, `/admin`).

The last point has a catch: the refresh cookie belongs to the **backend** (`localhost:8080`, or `api.bookhive.lk`) and is httpOnly. The **Next.js server never sees it**, so it can't know whether someone is logged in.

## Decision

### Access token: in a JavaScript variable

- Kept in a module-level variable in `src/features/auth/session.ts`. Never `localStorage`, `sessionStorage` or a readable cookie.
- `lib/api.ts` reads it through `setAccessTokenProvider` (step 63) and sends `Authorization: Bearer ...`.
- Lost on reload by design; see below.

### Getting a token: one refresh at a time

- `refreshSession()` calls `POST /api/v1/auth/refresh`; the browser adds the cookie itself (`credentials: "include"`).
- **Single flight:** if several requests need a token at the same moment (page load, a burst of 401s, two components), they share **one** refresh call. Two parallel refreshes would send the same refresh token twice, and the backend's reuse detection (ADR 0001) would log the user out.
- **On app start** the session provider calls `refreshSession()` once: a valid cookie restores the session silently after a reload.
- **On a 401** the API client refreshes once and retries the request (step 113). A second 401 means the session is really over.

### Telling Next.js "probably logged in": a hint cookie

- After login/refresh the frontend sets its **own** cookie on the frontend's domain: the user's roles, e.g. `bookhive_session=MEMBER` or `bookhive_session=LIBRARIAN.ADMIN` (not httpOnly, `SameSite=Lax`, 7 days). Logout and a failed refresh delete it.
- It contains **no secret**: only "this browser had a session, with these roles".
- The Next.js proxy (step 114) uses it to redirect visitors without it to `/login?next=...` before rendering protected pages, and sends members away from `/staff` and `/admin`. Once the session is restored, a client guard (`RequireArea`) checks the real roles again.
- **It is not security.** Faking it only shows an empty page shell: every piece of data still comes from the API, which checks the real token. Protected pages render their data on the client after the session is restored.

### Several tabs: BroadcastChannel

- Logging out in one tab tells the others (`BroadcastChannel("bookhive-auth")`), which clear their session too.
- Each tab restores its own access token with `refreshSession()`; the single-flight rule is per tab, so tabs refreshing at the very same instant remain a (rare) race. Acceptable; revisit with a cross-tab lock if it shows up in practice.

## Alternatives considered

- **Access token in `localStorage`.** Survives reloads with no refresh call, but any injected script can read it and send it anywhere. Rejected (ADR 0001).
- **Proxy all API calls through Next.js (a BFF) and keep tokens in a Next.js httpOnly cookie.** Lets the Next.js server render logged-in pages and know the user. More secure against XSS, but doubles the hops for every API call and duplicates auth logic in two servers. Too much for v1; keep as an option if server-rendered member pages become important.
- **Read the refresh cookie in the Next.js proxy.** Impossible: it's scoped to the backend's origin and path.

## Consequences

- ✅ No token is ever stored where a script could find it after the page closes.
- ✅ Logged-out visitors are redirected before a protected page renders.
- ⚠️ Member/staff/admin pages render their data in the browser (TanStack Query), not on the server. Fine: they're private, so SEO doesn't matter.
- ⚠️ Every reload costs one `/auth/refresh` call before the first protected API request.
- ⚠️ The hint cookie can be stale (e.g. the session expired on the server): the first API call then fails, the refresh fails, and the user is sent to login. That is the correct outcome, one step later.
