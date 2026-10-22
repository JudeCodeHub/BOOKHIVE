import { Link } from "@/i18n/navigation";
import {
  BarChart3,
  Clock,
  IdCard,
  LayoutDashboard,
  Info,
  ScrollText,
  SlidersHorizontal,
  UserCog,
} from "lucide-react";
import { ConsoleShell } from "@/components/layout/console-shell";
import type { NavItem } from "@/components/layout/sidebar-nav";

// Admin console pages (PRD §10): library rules and configuration, not daily desk work.
const ADMIN_NAV: NavItem[] = [
  { href: "/admin", label: "Overview", icon: <LayoutDashboard /> },
  { href: "/admin/settings", label: "Library rules", icon: <SlidersHorizontal /> },
  { href: "/admin/membership-types", label: "Membership types", icon: <IdCard /> },
  { href: "/admin/hours", label: "Hours & holidays", icon: <Clock /> },
  { href: "/admin/staff", label: "Staff accounts", icon: <UserCog /> },
  { href: "/admin/reports", label: "Reports", icon: <BarChart3 /> },
  { href: "/admin/audit-logs", label: "Audit log", icon: <ScrollText /> },
  { href: "/admin/library-info", label: "Library info", icon: <Info /> },
];

/** Admin console: same frame as the staff console, with a shortcut back to the desk. */
export default function AdminLayout({ children }: LayoutProps<"/[locale]/admin">) {
  return (
    <ConsoleShell
      badge="Admin"
      homeHref="/admin"
      nav={ADMIN_NAV}
      navLabel="Admin console"
      headerActions={
        // Admins are also librarians, so they often switch between the two consoles
        <Link href="/staff" className="text-sm underline-offset-4 hover:underline">
          Staff console
        </Link>
      }
    >
      {children}
    </ConsoleShell>
  );
}
