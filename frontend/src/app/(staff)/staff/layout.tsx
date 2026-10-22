import Link from "next/link";
import {
  BookCopy,
  BookMarked,
  CalendarDays,
  ClipboardCheck,
  Gift,
  LayoutDashboard,
  Lightbulb,
  Newspaper,
  ScanBarcode,
  Star,
  UserCheck,
  Users,
  Wallet,
  Repeat,
} from "lucide-react";
import { LogoMark } from "@/components/brand/logo";
import { SidebarNav, type NavItem } from "@/components/layout/sidebar-nav";
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

/**
 * Staff console: dense and desktop-first. A fixed top bar with a quick member/item lookup and a
 * compact sidebar, so librarians can work fast with keyboard and barcode scanner.
 */
export default function StaffLayout({ children }: LayoutProps<"/staff">) {
  return (
    <div className="flex min-h-full flex-1 flex-col">
      <header className="flex h-12 items-center gap-4 bg-secondary px-4 text-secondary-foreground">
        <Link href="/staff" className="flex items-center gap-2 rounded-md">
          <LogoMark className="size-6" />
          <span className="font-heading font-semibold">BookHive</span>
          <span className="rounded bg-primary px-1.5 py-0.5 text-xs font-medium text-primary-foreground">
            Staff
          </span>
        </Link>
        <form action="/staff/members" role="search" className="ml-auto w-full max-w-sm">
          <label htmlFor="staff-lookup" className="sr-only">
            Find a member or item
          </label>
          <Input
            id="staff-lookup"
            name="q"
            type="search"
            placeholder="Member no, name, NIC or barcode"
            className="h-8 border-white/20 bg-white/10 text-secondary-foreground placeholder:text-secondary-foreground/60"
          />
        </form>
      </header>

      <div className="flex flex-1 flex-col md:flex-row">
        <aside className="border-b bg-sidebar p-2 md:w-48 md:shrink-0 md:border-r md:border-b-0">
          <SidebarNav items={STAFF_NAV} label="Staff console" dense />
        </aside>
        <main id="main" className="min-w-0 flex-1 p-4">
          {children}
        </main>
      </div>
    </div>
  );
}
