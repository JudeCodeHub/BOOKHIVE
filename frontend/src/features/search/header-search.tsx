"use client";

import { useEffect, useId, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { BookOpen, Search, UserRound } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useRouter } from "@/i18n/navigation";
import { api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type Suggestion = Schema<"SuggestionSummary">;

/**
 * The header search box with suggestions as you type (checklist step 199). Still a normal GET
 * form, so searching works without JavaScript; with it, a list of matching books and authors
 * appears after a short pause (debounced), navigable with the arrow keys.
 */
export function HeaderSearch() {
  const t = useTranslations("Header");
  const tSearch = useTranslations("Search");
  const locale = useLocale();
  const router = useRouter();
  const listId = useId();
  const [text, setText] = useState("");
  const [debounced, setDebounced] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);

  // Ask the server only after typing pauses for a moment
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(text.trim()), 200);
    return () => clearTimeout(timer);
  }, [text]);

  const suggestions = useQuery({
    queryKey: ["suggest", debounced],
    queryFn: () => api.get<Suggestion[]>("/search/suggest", { query: { q: debounced } }),
    enabled: debounced.length >= 2,
    staleTime: 60_000,
  });
  const items = debounced.length >= 2 ? (suggestions.data ?? []) : [];
  const showList = open && items.length > 0;

  function go(item: Suggestion) {
    setOpen(false);
    setText("");
    router.push(item.type === "BOOK" ? `/books/${item.id}` : `/authors/${item.id}`);
  }

  return (
    <form
      action={`/${locale}/search`}
      role="search"
      className="relative order-last flex w-full items-center gap-2 sm:order-0 sm:w-auto sm:flex-1"
      onSubmit={(event) => {
        event.preventDefault();
        if (active >= 0 && items[active]) return go(items[active]);
        setOpen(false);
        router.push(`/search?q=${encodeURIComponent(text.trim())}`);
      }}
    >
      <label htmlFor="site-search" className="sr-only">
        {t("searchLabel")}
      </label>
      <Input
        id="site-search"
        name="q"
        type="search"
        autoComplete="off"
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
        placeholder={t("searchPlaceholder")}
        className="h-10"
        value={text}
        onChange={(event) => {
          setText(event.target.value);
          setOpen(true);
          setActive(-1);
        }}
        onFocus={() => setOpen(true)}
        // Delay so a click on a suggestion lands before the list closes
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && items.length > 0) {
            event.preventDefault();
            setOpen(true);
            setActive((i) => Math.min(i + 1, items.length - 1));
          } else if (event.key === "ArrowUp") {
            event.preventDefault();
            setActive((i) => Math.max(i - 1, -1));
          } else if (event.key === "Escape") {
            setOpen(false);
          }
        }}
      />
      <Button type="submit" size="icon" className="size-10 shrink-0" aria-label={t("search")}>
        <Search />
      </Button>

      {showList && (
        <ul
          id={listId}
          role="listbox"
          aria-label={tSearch("suggestions")}
          className="absolute top-full right-12 left-0 z-30 mt-1 max-h-96 overflow-y-auto rounded-lg border bg-popover p-1 text-sm shadow-lg"
        >
          {items.map((item, index) => (
            <li
              key={`${item.type}-${item.id}`}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={index === active}
              className={cn(
                "flex cursor-pointer items-start gap-2 rounded-md px-2 py-1.5",
                index === active && "bg-accent text-accent-foreground",
              )}
              onMouseEnter={() => setActive(index)}
              // mousedown fires before the input's blur closes the list
              onMouseDown={(event) => {
                event.preventDefault();
                go(item);
              }}
            >
              {item.type === "BOOK" ? (
                <BookOpen aria-hidden className="mt-0.5 size-4 shrink-0 text-primary" />
              ) : (
                <UserRound aria-hidden className="mt-0.5 size-4 shrink-0 text-primary" />
              )}
              <span className="min-w-0">
                <span className="block truncate font-medium">{item.label}</span>
                <span className="block truncate text-xs text-muted-foreground">
                  {item.type === "AUTHOR" ? tSearch("author") : item.detail}
                </span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </form>
  );
}
