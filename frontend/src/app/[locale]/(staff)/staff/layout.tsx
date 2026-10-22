import {
  BookCopy,
  BookMarked,
  CalendarDays,
  ClipboardCheck,
  Gift,
  LayoutDashboard,
  Lightbulb,
  Newspaper,
  Repeat,
  ScanBarcode,
  Star,
  UserCheck,
  Users,
  Wallet,
} from "lucide-react";
import { ConsoleShell } from "@/components/layout/console-shell";
import type { NavItem } from "@/components/layout/sidebar-nav";
import { Input } from "@/components/ui/input";

// Staff console pages (PRD §10). Desk first: it's what librarians use all day.
const STAFF_NAV: NavItem[] = [
  { href: "/staff", label: "Dashboard", icon: <LayoutDashboard /> },
  { href: "/staff/desk", label: "Desk", icon: <ScanBarcode /> },
  { href: "/staff/members", label: "Members", icon: <Users /> },
  { href: "/staff/applications", label: "Applications", icon: <UserCheck /> },
  { href: "/staff/catalog", label: "Catalog", icon: <BookCopy /> },
  { href: "/staff/loans", label: "Loans", icon: <Repeat /> },
  { href: "/staff/holds", label: "Hold shelf", icon: <BookMarked /> },
  { href: "/staff/fines", label: "Fines", icon: <Wallet /> },
  { href: "/staff/events", label: "Events", icon: <CalendarDays /> },
  { href: "/staff/news", label: "News", icon: <Newspaper /> },
  { href: "/staff/reviews", label: "Reviews", icon: <Star /> },
  { href: "/staff/donations", label: "Donations", icon: <Gift /> },
  { href: "/staff/suggestions", label: "Suggestions", icon: <Lightbulb /> },
  { href: "/staff/stocktake", label: "Stock-take", icon: <ClipboardCheck /> },
];

export default function StaffLayout({ children }: LayoutProps<"/[locale]/staff">) {
  return (
    <ConsoleShell
      badge="Staff"
      homeHref="/staff"
      nav={STAFF_NAV}
      navLabel="Staff console"
      headerActions={
        // Quick lookup: scan a member card or item barcode from any staff page
        <form action="/staff/members" role="search" className="w-full max-w-sm">
          <label htmlFor="staff-lookup" className="sr-only">
            Find a member or item
          </label>
          <Input
            id="staff-lookup"
            name="q"
            type="search"
            placeholder="Member no, name, NIC or barcode"
            className="h-8 w-full border-white/20 bg-white/10 text-secondary-foreground placeholder:text-secondary-foreground/60 sm:w-72"
          />
        </form>
      }
    >
      {children}
    </ConsoleShell>
  );
}
