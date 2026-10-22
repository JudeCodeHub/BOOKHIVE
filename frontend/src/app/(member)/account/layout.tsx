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
import { SiteFooter } from "@/components/layout/site-footer";
import { SiteHeader } from "@/components/layout/site-header";
import { SidebarNav, type NavItem } from "@/components/layout/sidebar-nav";

// Member portal pages (PRD §10)
const MEMBER_NAV: NavItem[] = [
  { href: "/account", label: "Dashboard", icon: <LayoutDashboard /> },
  { href: "/account/loans", label: "My loans", icon: <BookOpen /> },
  { href: "/account/holds", label: "Holds", icon: <BookMarked /> },
  { href: "/account/fines", label: "Fines & payments", icon: <Wallet /> },
  { href: "/account/history", label: "Reading history", icon: <History /> },
  { href: "/account/card", label: "Library card", icon: <CreditCard /> },
  { href: "/account/wishlist", label: "Wishlist", icon: <Heart /> },
  { href: "/account/events", label: "My events", icon: <CalendarDays /> },
  { href: "/account/notifications", label: "Notifications", icon: <Bell /> },
  { href: "/account/settings", label: "Settings", icon: <Settings /> },
];

/** Member portal: same header as the public site, plus a sidebar. Mobile-first. */
export default function MemberLayout({ children }: LayoutProps<"/account">) {
  return (
    <>
      <SiteHeader />
      <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-4 py-6 md:flex-row">
        <aside className="md:w-56 md:shrink-0">
          <SidebarNav items={MEMBER_NAV} label="My account" />
        </aside>
        <main id="main" className="min-w-0 flex-1">
          {children}
        </main>
      </div>
      <SiteFooter />
    </>
  );
}
