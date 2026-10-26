import { useTranslations } from "next-intl";
import { Logo } from "@/components/brand/logo";
import { AccountMenu } from "@/components/layout/account-menu";
import { LanguageSwitcher } from "@/components/layout/language-switcher";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { HeaderSearch } from "@/features/search/header-search";
import { Link } from "@/i18n/navigation";

/** Public + member header: logo, catalog search, language, login / account menu. */
export function SiteHeader() {
  const t = useTranslations("Header");

  return (
    <header className="border-b bg-card">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3">
        <Link href="/" aria-label={t("home")} className="rounded-md">
          <Logo />
        </Link>

        {/* A plain GET form (works without JavaScript, shareable /en/search?q= URL) with
            suggestions as you type */}
        <HeaderSearch />

        <div className="ml-auto flex items-center gap-3 sm:ml-0">
          <LanguageSwitcher />
          <ThemeToggle />
          <AccountMenu />
        </div>
      </div>
    </header>
  );
}
