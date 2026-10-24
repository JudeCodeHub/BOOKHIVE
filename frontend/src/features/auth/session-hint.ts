/**
 * The "probably logged in" hint cookie (ADR 0002), shared by the browser session code and the
 * Next.js proxy. No side effects here: the proxy runs before React and can't load browser code.
 *
 * Value: the user's roles, e.g. "MEMBER" or "LIBRARIAN.ADMIN". It is NOT security: anyone can
 * edit it, and that only shows an empty page shell. Every piece of data still comes from the API,
 * which checks the real access token.
 */
export const SESSION_HINT_COOKIE = "bookhive_session";
export const SESSION_HINT_MAX_AGE_SECONDS = 7 * 24 * 60 * 60; // as long as the refresh token

export type HintRole = "MEMBER" | "LIBRARIAN" | "ADMIN";
const ROLES: readonly HintRole[] = ["MEMBER", "LIBRARIAN", "ADMIN"];

export function encodeHint(roles: readonly string[]): string {
  return roles.filter((role): role is HintRole => ROLES.includes(role as HintRole)).join(".");
}

/** Roles from the cookie value; null if the cookie is missing or empty. */
export function decodeHint(value: string | undefined | null): HintRole[] | null {
  if (!value) return null;
  const roles = value
    .split(".")
    .filter((role): role is HintRole => ROLES.includes(role as HintRole));
  return roles.length > 0 ? roles : null;
}

/** The protected areas of the site and who may open them (same rules as the backend). */
export type Area = "account" | "staff" | "admin";
const AREAS: readonly Area[] = ["account", "staff", "admin"];

export function canOpen(area: Area, roles: readonly string[]): boolean {
  switch (area) {
    case "account":
      return roles.length > 0; // any logged-in user
    case "staff":
      return roles.includes("LIBRARIAN") || roles.includes("ADMIN"); // admin is also a librarian
    case "admin":
      return roles.includes("ADMIN");
  }
}

/**
 * The areas a user may open, from least to most powerful: ["account"] for a member,
 * ["account", "staff", "admin"] for an admin. The last one is the user's "home" area.
 */
export function openableAreas(roles: readonly string[]): Area[] {
  return AREAS.filter((area) => canOpen(area, roles));
}
