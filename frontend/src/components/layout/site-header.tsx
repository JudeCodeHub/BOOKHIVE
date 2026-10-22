import Link from "next/link";
import { Search } from "lucide-react";
import { Logo } from "@/components/brand/logo";
import { LanguageSwitcher } from "@/components/layout/language-switcher";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

/** Public + member header: logo, catalog search, language, login. */
export function SiteHeader() {
  return (
    <header className="border-b bg-card">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3">
        <Link href="/" aria-label="BookHive home" className="rounded-md">
          <Logo />
        </Link>

        {/* A plain GET form: works without JavaScript and gives a shareable /search?q= URL */}
        <form
          action="/search"
          role="search"
          className="order-last flex w-full items-center gap-2 sm:order-0 sm:w-auto sm:flex-1"
        >
          <label htmlFor="site-search" className="sr-only">
            Search the catalog
          </label>
          <Input
            id="site-search"
            name="q"
            type="search"
            placeholder="Search by title, author or ISBN"
            className="h-10"
          />
          <Button type="submit" size="icon" className="size-10 shrink-0" aria-label="Search">
            <Search />
          </Button>
        </form>

        <div className="ml-auto flex items-center gap-3 sm:ml-0">
          <LanguageSwitcher />
          <ThemeToggle />
          <Button asChild variant="secondary">
            <Link href="/login">Log in</Link>
          </Button>
        </div>
      </div>
    </header>
  );
}
