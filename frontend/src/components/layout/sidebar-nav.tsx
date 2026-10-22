"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

export type NavItem = { href: string; label: string; icon: ReactNode };

/**
 * Sidebar links with the current page highlighted. A client component because it reads the
 * current URL. Vertical on desktop; on small screens it becomes a horizontally scrolling row.
 */
export function SidebarNav({
  items,
  label,
  dense = false,
}: {
  items: NavItem[];
  label: string;
  dense?: boolean;
}) {
  const pathname = usePathname();

  // Exact match for section roots (/account), prefix match for the rest (/account/loans/123)
  const isActive = (href: string) =>
    pathname === href || (href.split("/").length > 2 && pathname.startsWith(`${href}/`));

  return (
    <nav aria-label={label}>
      <ul className="flex gap-1 overflow-x-auto md:flex-col md:overflow-visible">
        {items.map((item) => (
          <li key={item.href} className="shrink-0">
            <Link
              href={item.href}
              aria-current={isActive(item.href) ? "page" : undefined}
              className={cn(
                "flex items-center gap-2 rounded-md font-medium whitespace-nowrap transition-colors [&_svg]:size-4 [&_svg]:shrink-0",
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
