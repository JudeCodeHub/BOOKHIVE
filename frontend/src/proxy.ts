import { NextResponse, type NextRequest } from "next/server";
import createMiddleware from "next-intl/middleware";
import { guardRedirect } from "@/features/auth/route-guard";
import { SESSION_HINT_COOKIE } from "@/features/auth/session-hint";
import { routing } from "@/i18n/routing";

const intl = createMiddleware(routing);

/**
 * Runs before every page request (Next.js 16 calls this file proxy.ts; it used to be
 * middleware.ts).
 *
 * 1. Protected areas (/account, /staff, /admin): visitors without a session are sent to the login
 *    page first, and members can't open the staff/admin consoles (ADR 0002).
 * 2. Languages: a URL without one, like "/account", is redirected to "/en/account" (or the
 *    language the user picked before / their browser prefers).
 */
export default function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const redirectTo = guardRedirect(
    pathname,
    search,
    request.cookies.get(SESSION_HINT_COOKIE)?.value,
  );
  if (redirectTo) {
    return NextResponse.redirect(new URL(redirectTo, request.url));
  }
  return intl(request);
}

export const config = {
  // Everything except API routes, Next.js internals and files with a dot (icon.svg, robots.txt)
  matcher: "/((?!api|_next|_vercel|.*\\..*).*)",
};
