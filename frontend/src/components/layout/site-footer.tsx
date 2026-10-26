import { useTranslations } from "next-intl";
import { LogoMark } from "@/components/brand/logo";
import { OpenNowBadge } from "@/features/library/open-now-badge";
import { Link } from "@/i18n/navigation";

export function SiteFooter() {
  const t = useTranslations("Footer");

  const links = [
    { href: "/visit", label: t("visit") },
    { href: "/rules", label: t("rules") },
    { href: "/events", label: t("events") },
    { href: "/privacy", label: t("privacy") },
    { href: "/terms", label: t("terms") },
  ];

  return (
    <footer className="mt-auto border-t bg-secondary text-secondary-foreground">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-8 sm:flex-row sm:items-center sm:justify-between">
        <div className="grid gap-2">
          <div className="flex items-center gap-2">
            <LogoMark className="size-6" />
            <span className="font-heading font-semibold">BookHive</span>
          </div>
          <OpenNowBadge />
        </div>
        <nav aria-label={t("label")}>
          <ul className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
            {links.map((link) => (
              <li key={link.href}>
                <Link href={link.href} className="underline-offset-4 hover:underline">
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      </div>
    </footer>
  );
}
