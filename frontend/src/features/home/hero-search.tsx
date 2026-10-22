import { Search } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

/** Search-first homepage (PRD §11): the big search box is the first thing people see. */
export function HeroSearch() {
  const t = useTranslations("Home");
  const header = useTranslations("Header");
  const locale = useLocale();

  return (
    <section className="border-b bg-honeycomb">
      <div className="mx-auto flex max-w-3xl flex-col items-center gap-5 px-4 py-16 text-center sm:py-24">
        <h1 className="text-4xl font-semibold text-balance sm:text-5xl">{t("heroTitle")}</h1>
        <p className="max-w-2xl text-lg text-pretty text-muted-foreground">{t("heroSubtitle")}</p>

        {/* Plain GET form: works without JavaScript, results URL can be shared */}
        <form
          action={`/${locale}/search`}
          role="search"
          className="mt-2 flex w-full max-w-xl items-center gap-2 rounded-xl border bg-card p-2 shadow-sm"
        >
          <label htmlFor="hero-search" className="sr-only">
            {header("searchLabel")}
          </label>
          <Input
            id="hero-search"
            name="q"
            type="search"
            placeholder={header("searchPlaceholder")}
            className="h-12 border-0 text-base shadow-none focus-visible:ring-0 md:text-base"
          />
          <Button type="submit" size="lg" className="h-12 shrink-0 px-5">
            <Search />
            <span className="hidden sm:inline">{header("search")}</span>
            <span className="sr-only sm:hidden">{header("search")}</span>
          </Button>
        </form>
      </div>
    </section>
  );
}
