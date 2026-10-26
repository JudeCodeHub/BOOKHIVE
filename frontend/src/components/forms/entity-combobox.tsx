"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Plus, X } from "lucide-react";
import { useTranslations } from "next-intl";
import type { FieldControlProps } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

/**
 * A chosen entity. Without an id it's a new one, typed by the user, to be created when the form
 * is saved (so abandoning the form leaves no half-made authors behind).
 */
export type EntityChoice = { id?: string; name: string };

/**
 * Type-ahead picker with "Create new" (checklist step 174), for authors and publishers. Follows
 * the ARIA combobox pattern: arrow keys move through the options, Enter picks, Escape closes.
 *
 *   <FormField label="Publisher">
 *     {(field) => <EntityCombobox {...field} value={publisher} onChange={setPublisher}
 *                   search={searchPublishers} queryKey="publishers" />}
 *   </FormField>
 */
export function EntityCombobox({
  value,
  onChange,
  search,
  queryKey,
  placeholder,
  allowCreate = true,
  ...field
}: FieldControlProps & {
  value: EntityChoice | null;
  onChange: (value: EntityChoice | null) => void;
  /** Matches for the typed text, best first */
  search: (text: string) => Promise<{ id: string; name: string }[]>;
  /** Cache key prefix, e.g. "authors" */
  queryKey: string;
  placeholder?: string;
  /** false = only existing entries can be chosen (no "Create" option) */
  allowCreate?: boolean;
}) {
  const t = useTranslations("Combobox");
  const listId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [text, setText] = useState("");
  const [debounced, setDebounced] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);

  // Ask the server only after typing pauses for a moment
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(text.trim()), 250);
    return () => clearTimeout(timer);
  }, [text]);

  const results = useQuery({
    queryKey: ["combobox", queryKey, debounced],
    queryFn: () => search(debounced),
    enabled: debounced.length >= 2,
    staleTime: 30_000,
  });

  if (value) {
    return (
      <div className="flex h-8 items-center gap-2 rounded-lg border px-2.5 text-sm">
        <span className="min-w-0 flex-1 truncate">{value.name}</span>
        {!value.id && (
          <span className="rounded bg-primary/15 px-1.5 text-xs font-medium">{t("new")}</span>
        )}
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="size-6"
          aria-label={t("clear", { name: value.name })}
          onClick={() => {
            onChange(null);
            setText("");
            // Back to typing straight away
            setTimeout(() => inputRef.current?.focus());
          }}
        >
          <X />
        </Button>
      </div>
    );
  }

  const matches = debounced.length >= 2 ? (results.data ?? []) : [];
  const typed = text.trim();
  const exact = matches.some((m) => m.name.toLowerCase() === typed.toLowerCase());
  // The options: matches, then "Create" when the typed name isn't one of them
  const options: EntityChoice[] = [
    ...matches,
    ...(allowCreate && typed.length >= 2 && !exact ? [{ name: typed }] : []),
  ];
  const showList = open && options.length > 0;

  function choose(option: EntityChoice) {
    onChange(option);
    setOpen(false);
    setText("");
    setActive(0);
  }

  return (
    <div className="relative">
      <Input
        ref={inputRef}
        {...field}
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={showList ? `${listId}-${active}` : undefined}
        autoComplete="off"
        placeholder={placeholder}
        value={text}
        onChange={(event) => {
          setText(event.target.value);
          setOpen(true);
          setActive(0);
        }}
        onFocus={() => setOpen(true)}
        // Delay so a click on an option lands before the list closes
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown") {
            event.preventDefault();
            setOpen(true);
            setActive((i) => Math.min(i + 1, options.length - 1));
          } else if (event.key === "ArrowUp") {
            event.preventDefault();
            setActive((i) => Math.max(i - 1, 0));
          } else if (event.key === "Enter" && showList) {
            // Enter picks instead of submitting the whole form
            event.preventDefault();
            choose(options[active]);
          } else if (event.key === "Escape") {
            setOpen(false);
          }
        }}
      />
      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-20 mt-1 max-h-60 w-full overflow-y-auto rounded-lg border bg-popover p-1 text-sm shadow-md"
        >
          {options.map((option, index) => (
            <li
              key={option.id ?? `new-${option.name}`}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={index === active}
              className={cn(
                "flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5",
                index === active && "bg-accent text-accent-foreground",
              )}
              onMouseEnter={() => setActive(index)}
              // mousedown, not click: it fires before the input's blur
              onMouseDown={(event) => {
                event.preventDefault();
                choose(option);
              }}
            >
              {option.id ? (
                option.name
              ) : (
                <>
                  <Plus aria-hidden className="size-4 text-primary" />
                  {t("create", { name: option.name })}
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
