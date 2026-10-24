import { routing } from "@/i18n/routing";
import { canOpen, decodeHint, openableAreas, type Area } from "@/features/auth/session-hint";

const PROTECTED = new RegExp(`^/(${routing.locales.join("|")})(/(account|staff|admin)(?:/.*)?)$`);

/**
 * Decides, before a page renders, whether to send the visitor elsewhere (checklist step 114).
 * Used by src/proxy.ts. Returns the path to redirect to, or null to let the request through.
 *
 * - No session hint -> the login page, remembering where they wanted to go (?next=)
 * - Logged in but the wrong role (a member opening /staff) -> their own area
 *
 * Only a convenience: the hint cookie can be faked, but that only shows an empty page; the API
 * refuses the data without a valid token.
 */
export function guardRedirect(pathname: string, search: string, hint: string | undefined) {
  const match = PROTECTED.exec(pathname);
  if (!match) return null;
  const [, locale, rest, area] = match;

  const roles = decodeHint(hint);
  if (!roles) {
    return `/${locale}/login?next=${encodeURIComponent(rest + search)}`;
  }
  if (!canOpen(area as Area, roles)) {
    return `/${locale}/${openableAreas(roles).at(-1)}`;
  }
  return null;
}
