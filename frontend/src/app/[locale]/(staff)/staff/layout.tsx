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
import { useLocale, useTranslations } from "next-intl";
import { ConsoleShell } from "@/components/layout/console-shell";
import type { NavItem } from "@/components/layout/sidebar-nav";
import { Input } from "@/components/ui/input";

export default function StaffLayout({ children }: LayoutProps<"/[locale]/staff">) {
  const t = useTranslations("StaffNav");
  const locale = useLocale();

  // Staff console pages (PRD §10). Desk first: it's what librarians use all day.
  const nav: NavItem[] = [
    { href: "/staff", label: t("dashboard"), icon: <LayoutDashboard /> },
    { href: "/staff/desk", label: t("desk"), icon: <ScanBarcode /> },
    { href: "/staff/members", label: t("members"), icon: <Users /> },
    { href: "/staff/applications", label: t("applications"), icon: <UserCheck /> },
    { href: "/staff/catalog", label: t("catalog"), icon: <BookCopy /> },
    { href: "/staff/loans", label: t("loans"), icon: <Repeat /> },
    { href: "/staff/holds", label: t("holds"), icon: <BookMarked /> },
    { href: "/staff/fines", label: t("fines"), icon: <Wallet /> },
    { href: "/staff/events", label: t("events"), icon: <CalendarDays /> },
    { href: "/staff/news", label: t("news"), icon: <Newspaper /> },
    { href: "/staff/reviews", label: t("reviews"), icon: <Star /> },
    { href: "/staff/donations", label: t("donations"), icon: <Gift /> },
    { href: "/staff/suggestions", label: t("suggestions"), icon: <Lightbulb /> },
    { href: "/staff/stocktake", label: t("stocktake"), icon: <ClipboardCheck /> },
  ];

  return (
    <ConsoleShell
      badge={t("badge")}
      homeHref="/staff"
      nav={nav}
      navLabel={t("label")}
      headerActions={
        // Quick lookup: scan a member card or item barcode from any staff page
        <form action={`/${locale}/staff/members`} role="search" className="w-full max-w-sm">
          <label htmlFor="staff-lookup" className="sr-only">
            {t("lookupLabel")}
          </label>
          <Input
            id="staff-lookup"
            name="q"
            type="search"
            placeholder={t("lookupPlaceholder")}
            className="h-8 w-full border-white/20 bg-white/10 text-secondary-foreground placeholder:text-secondary-foreground/60 sm:w-72"
          />
        </form>
      }
    >
      {children}
    </ConsoleShell>
  );
}
