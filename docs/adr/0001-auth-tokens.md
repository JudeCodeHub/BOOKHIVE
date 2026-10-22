# 0001. Login with a short-lived access JWT and a rotating refresh token cookie

- Status: Accepted
- Date: 2026-10-22
- Related: PRD AUTH-3, AUTH-8, §6 Security; checklist steps 85–96 and 107–113

## Context

BookHive has a Next.js frontend and a separate Spring Boot API. After logging in, every API call must prove who the user is and which roles they have (member, librarian, admin).

What matters:

- **Theft resistance.** The main web threat is XSS: injected JavaScript reading anything the page can read. A stolen login must be useless quickly, and detectable.
- **Members stay logged in.** Nimali renews books from her phone a few times a week; she shouldn't log in every 15 minutes.
- **The API stays stateless and fast.** Checking permissions on every request shouldn't need a database lookup, and should work the same on 2+ backend instances.
- **Logout and "log out everywhere" must really work** (AUTH-8), and a password reset must end an attacker's session.

## Decision

Two tokens with different jobs:

### 1. Access token: short-lived JWT, kept in memory

- A JWT signed with **RS256** (private key on the server only; checking needs only the public key). Contains user id, roles and expiry.
- Lifetime **15 minutes**.
- Returned in the login response body. The frontend keeps it **in a JavaScript variable only**: never `localStorage` or `sessionStorage`, which any injected script can read and which survive closing the tab.
- Sent as `Authorization: Bearer <token>`. The API verifies the signature and expiry: no database lookup.

### 2. Refresh token: long-lived, random, httpOnly cookie

- A random 256-bit value (not a JWT), lifetime **7 days**.
- Set as a cookie: `HttpOnly` (JavaScript can't read it, so XSS can't steal it), `Secure` (HTTPS only), `SameSite=Strict` (not sent on requests started by other sites), `Path=/api/v1/auth` (sent only to the auth endpoints, nowhere else).
- The database stores only its **SHA-256 hash** (`refresh_tokens.token_hash`), so a database leak doesn't expose usable tokens.
- **Rotation:** every `POST /auth/refresh` revokes the used token and issues a new one (same `family_id`), plus a new access token.
- **Reuse detection:** if an already-used refresh token comes back, someone copied it. The whole family is revoked, logging out both the attacker and the real user, who simply logs in again.

### Flows

| Action | What happens |
|---|---|
| Login | Access token in the body + refresh cookie |
| Page load / app start | Frontend calls `/auth/refresh` to get an access token (the cookie is sent automatically) |
| Access token expired (401) | Frontend silently calls `/auth/refresh` once, then retries the request |
| Logout | Refresh token revoked, cookie cleared; the access token dies within 15 min |
| Log out everywhere / password reset | All the user's refresh tokens revoked |

## Alternatives considered

- **Server-side sessions (`JSESSIONID` cookie).** Simple and revocable, but every request needs a session lookup and the session store must be shared across instances (Redis). Our API also serves a separate frontend origin. Rejected to keep the API stateless; the refresh-token table gives us revocation where it matters.
- **One long-lived JWT in `localStorage`.** The most common tutorial approach and the least safe: any XSS reads it, and it can't be revoked until it expires. Rejected.
- **Access JWT in an httpOnly cookie too.** Safe from XSS, but then every API endpoint needs CSRF protection, not just two auth endpoints. Rejected; the Bearer header can't be forged by other sites.
- **Refresh token as a JWT.** Would need a database check anyway to support rotation and revocation, so the signature adds nothing. A random value plus a stored hash is simpler.

## Consequences

- ✅ An XSS bug can at worst use the in-memory access token for ≤ 15 minutes; it can't steal the refresh token.
- ✅ A stolen refresh token is detected the first time both copies are used.
- ✅ Permission checks need no database lookup; any backend instance can verify tokens.
- ⚠️ Roles inside a JWT are stale for up to 15 minutes after a change (e.g. a librarian is deactivated). Acceptable; admins can also revoke refresh tokens to force it.
- ⚠️ `SameSite=Strict` requires the frontend and API to be the **same site** (`localhost` in dev; `bookhive.lk` + `api.bookhive.lk`, or one domain with `/api` routed to the backend, in production).
- ⚠️ The refresh and logout endpoints rely on cookies, so they still get CSRF protection (checklist step 393).
- ⚠️ The RS256 private key is a secret: an env var / secrets manager in production, never committed. Keys get a `kid` so they can be rotated.
- ⚠️ A page reload loses the in-memory access token, costing one `/auth/refresh` call at startup.
