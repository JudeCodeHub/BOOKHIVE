import { Search } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { Logo } from "@/components/brand/logo";
import { AccountButton } from "@/components/layout/account-button";
import { LanguageSwitcher } from "@/components/layout/language-switcher";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Link } from "@/i18n/navigation";

/** Public + member header: logo, catalog search, language, login. */
export function SiteHeader() {
  const t = useTranslations("Header");
  const locale = useLocale();

  return (
    <header className="border-b bg-card">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3">
        <Link href="/" aria-label={t("home")} className="rounded-md">
          <Logo />
        </Link>

        {/* A plain GET form: works without JavaScript and gives a shareable /en/search?q= URL */}
        <form
          action={`/${locale}/search`}
          role="search"
          className="order-last flex w-full items-center gap-2 sm:order-0 sm:w-auto sm:flex-1"
        >
          <label htmlFor="site-search" className="sr-only">
            {t("searchLabel")}
          </label>
          <Input
            id="site-search"
            name="q"
            type="search"
            placeholder={t("searchPlaceholder")}
            className="h-10"
          />
          <Button type="submit" size="icon" className="size-10 shrink-0" aria-label={t("search")}>
            <Search />
          </Button>
        </form>

        <div className="ml-auto flex items-center gap-3 sm:ml-0">
          <LanguageSwitcher />
          <ThemeToggle />
          <AccountButton />
        </div>
      </div>
    </header>
  );
}
