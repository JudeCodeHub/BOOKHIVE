import {
  Bell,
  BookMarked,
  BookOpen,
  CalendarDays,
  CreditCard,
  Heart,
  History,
  LayoutDashboard,
  Settings,
  Wallet,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { SiteFooter } from "@/components/layout/site-footer";
import { SiteHeader } from "@/components/layout/site-header";
import { SidebarNav, type NavItem } from "@/components/layout/sidebar-nav";
import { RequireArea } from "@/features/auth/require-area";

/** Member portal: same header as the public site, plus a sidebar. Mobile-first. */
export default function MemberLayout({ children }: LayoutProps<"/[locale]/account">) {
  const t = useTranslations("MemberNav");

  // Member portal pages (PRD §10)
  const nav: NavItem[] = [
    { href: "/account", label: t("dashboard"), icon: <LayoutDashboard /> },
    { href: "/account/loans", label: t("loans"), icon: <BookOpen /> },
    { href: "/account/holds", label: t("holds"), icon: <BookMarked /> },
    { href: "/account/fines", label: t("fines"), icon: <Wallet /> },
    { href: "/account/history", label: t("history"), icon: <History /> },
    { href: "/account/card", label: t("card"), icon: <CreditCard /> },
    { href: "/account/wishlist", label: t("wishlist"), icon: <Heart /> },
    { href: "/account/events", label: t("events"), icon: <CalendarDays /> },
    { href: "/account/notifications", label: t("notifications"), icon: <Bell /> },
    { href: "/account/settings", label: t("settings"), icon: <Settings /> },
  ];

  return (
    <>
      <SiteHeader />
      <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-4 py-6 md:flex-row">
        <aside className="md:w-56 md:shrink-0">
          <SidebarNav items={nav} label={t("label")} />
        </aside>
        <main id="main" className="min-w-0 flex-1">
          <RequireArea area="account">{children}</RequireArea>
        </main>
      </div>
      <SiteFooter />
    </>
  );
}
