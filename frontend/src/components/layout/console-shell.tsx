import type { ReactNode } from "react";
import { Link } from "@/i18n/navigation";
import { LogoMark } from "@/components/brand/logo";
import { AccountMenu } from "@/components/layout/account-menu";
import { NotificationBell } from "@/components/layout/notification-bell";
import { SidebarNav, type NavItem } from "@/components/layout/sidebar-nav";
import { TextSizeToggle } from "@/components/theme/text-size-toggle";
import { ThemeToggle } from "@/components/theme/theme-toggle";

/**
 * Frame shared by the staff and admin consoles: dense and desktop-first, with a dark top bar
 * and a compact sidebar, so staff can work fast with keyboard and barcode scanner.
 */
export function ConsoleShell({
  badge,
  homeHref,
  nav,
  navLabel,
  headerActions,
  children,
}: {
  /** Shown next to the logo, e.g. "Staff" or "Admin" */
  badge: string;
  homeHref: string;
  nav: NavItem[];
  navLabel: string;
  /** Right side of the top bar, e.g. a quick search or a link to the other console */
  headerActions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="flex min-h-full flex-1 flex-col">
      <header className="flex h-12 items-center gap-4 bg-secondary px-4 text-secondary-foreground">
        <Link href={homeHref} className="flex shrink-0 items-center gap-2 rounded-md">
          <LogoMark className="size-6" />
          <span className="font-heading font-semibold">BookHive</span>
          <span className="rounded bg-primary px-1.5 py-0.5 text-xs font-medium text-primary-foreground">
            {badge}
          </span>
        </Link>
        <div className="ml-auto flex min-w-0 items-center gap-3">
          {headerActions}
          <TextSizeToggle className="hidden sm:flex" />
          <ThemeToggle className="hover:bg-white/10 hover:text-secondary-foreground" />
          <NotificationBell className="hover:bg-white/10 hover:text-secondary-foreground" />
          <AccountMenu className="h-8 bg-white/10 text-secondary-foreground hover:bg-white/20" />
        </div>
      </header>

      <div className="flex flex-1 flex-col md:flex-row">
        <aside className="border-b bg-sidebar p-2 md:w-48 md:shrink-0 md:border-r md:border-b-0">
          <SidebarNav items={nav} label={navLabel} dense />
        </aside>
        <main id="main" className="min-w-0 flex-1 p-4">
          {children}
        </main>
      </div>
    </div>
  );
}
