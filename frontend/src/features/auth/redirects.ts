import type { SessionUser } from "@/features/auth/session";
import { openableAreas } from "@/features/auth/session-hint";

/**
 * Where to go after logging in: the page the user was trying to reach (?next=), or their area.
 * An admin lands in the admin console, a librarian at the staff desk, a member in My account.
 */
export function afterLoginPath(user: SessionUser, next: string | null): string {
  if (next && isSafeInternalPath(next)) {
    return next;
  }
  return `/${openableAreas(user.roles).at(-1) ?? "account"}`;
}

/**
 * Only paths inside this site. Blocks "open redirect" tricks such as
 * /login?next=https://evil.example or //evil.example, which would send a freshly logged-in user
 * to a fake site after a real login.
 */
export function isSafeInternalPath(path: string): boolean {
  return path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/\\");
}
