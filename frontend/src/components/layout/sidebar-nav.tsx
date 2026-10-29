"use client";

import { Suspense, type ReactNode } from "react";
import { Link, usePathname } from "@/i18n/navigation";
import { cn } from "@/lib/utils";

export type NavItem = { href: string; label: string; icon: ReactNode };

type Props = { items: NavItem[]; label: string; dense?: boolean };

/**
 * Sidebar links with the current page highlighted. A client component because it reads the
 * current URL. Vertical on desktop; on small screens it becomes a horizontally scrolling row.
 *
 * On pages with an id in the URL (/staff/members/[id]) the path isn't known when the page shell
 * is pre-rendered, so the links first render without a highlight (Suspense fallback).
 */
export function SidebarNav(props: Props) {
  return (
    <Suspense fallback={<NavLinks {...props} pathname={null} />}>
      <CurrentNavLinks {...props} />
    </Suspense>
  );
}

function CurrentNavLinks(props: Props) {
  return <NavLinks {...props} pathname={usePathname()} />;
}

function NavLinks({ items, label, dense = false, pathname }: Props & { pathname: string | null }) {
  // Exact match for section roots (/account), prefix match for the rest (/account/loans/123)
  const isActive = (href: string) =>
    pathname !== null &&
    (pathname === href || (href.split("/").length > 2 && pathname.startsWith(`${href}/`)));

  return (
    <nav aria-label={label}>
      <ul className="flex gap-1 overflow-x-auto md:flex-col md:overflow-visible">
        {items.map((item) => (
          <li key={item.href} className="shrink-0">
            <Link
              href={item.href}
              aria-current={isActive(item.href) ? "page" : undefined}
              className={cn(
                // Phones scroll the row sideways, so labels stay on one line; the sidebar on wider
                // screens is a fixed width, where long Sinhala and Tamil labels wrap instead
                "flex items-center gap-2 rounded-md font-medium whitespace-nowrap transition-colors md:whitespace-normal [&_svg]:size-4 [&_svg]:shrink-0",
                dense ? "px-2 py-1.5 text-sm" : "px-3 py-2",
                isActive(item.href)
                  ? "bg-sidebar-primary text-sidebar-primary-foreground"
                  : "text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
              )}
            >
              {item.icon}
              {item.label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
