import createMiddleware from "next-intl/middleware";
import { routing } from "@/i18n/routing";

/**
 * Runs before every page request (Next.js 16 calls this file proxy.ts; it used to be
 * middleware.ts). A URL without a language, like "/account", is redirected to "/en/account"
 * (or the language the user picked before / their browser prefers).
 */
export default createMiddleware(routing);

export const config = {
  // Everything except API routes, Next.js internals and files with a dot (icon.svg, robots.txt)
  matcher: "/((?!api|_next|_vercel|.*\\..*).*)",
};
