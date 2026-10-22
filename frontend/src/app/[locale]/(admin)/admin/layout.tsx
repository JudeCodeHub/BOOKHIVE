import {
  BarChart3,
  Clock,
  IdCard,
  Info,
  LayoutDashboard,
  ScrollText,
  SlidersHorizontal,
  UserCog,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { ConsoleShell } from "@/components/layout/console-shell";
import type { NavItem } from "@/components/layout/sidebar-nav";
import { Link } from "@/i18n/navigation";

/** Admin console: same frame as the staff console, with a shortcut back to the desk. */
export default function AdminLayout({ children }: LayoutProps<"/[locale]/admin">) {
  const t = useTranslations("AdminNav");

  // Admin console pages (PRD §10): library rules and configuration, not daily desk work.
  const nav: NavItem[] = [
    { href: "/admin", label: t("overview"), icon: <LayoutDashboard /> },
    { href: "/admin/settings", label: t("settings"), icon: <SlidersHorizontal /> },
    { href: "/admin/membership-types", label: t("membershipTypes"), icon: <IdCard /> },
    { href: "/admin/hours", label: t("hours"), icon: <Clock /> },
    { href: "/admin/staff", label: t("staff"), icon: <UserCog /> },
    { href: "/admin/reports", label: t("reports"), icon: <BarChart3 /> },
    { href: "/admin/audit-logs", label: t("auditLogs"), icon: <ScrollText /> },
    { href: "/admin/library-info", label: t("libraryInfo"), icon: <Info /> },
  ];

  return (
    <ConsoleShell
      badge={t("badge")}
      homeHref="/admin"
      nav={nav}
      navLabel={t("label")}
      headerActions={
        // Admins are also librarians, so they often switch between the two consoles
        <Link href="/staff" className="text-sm underline-offset-4 hover:underline">
          {t("staffConsole")}
        </Link>
      }
    >
      {children}
    </ConsoleShell>
  );
}
