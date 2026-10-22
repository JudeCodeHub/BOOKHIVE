import { createNavigation } from "next-intl/navigation";
import { routing } from "@/i18n/routing";

/**
 * Use these instead of next/link and next/navigation: they add the current language to URLs.
 * <Link href="/account"> goes to /si/account on a Sinhala page, and usePathname() returns the
 * path without the language ("/account").
 */
export const { Link, redirect, usePathname, useRouter, getPathname } = createNavigation(routing);
