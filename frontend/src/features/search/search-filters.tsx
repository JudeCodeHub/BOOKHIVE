"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { SearchState } from "@/features/search/search-params";
import type { Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type Facets = Schema<"FacetsResponse">;
type CategoryNode = Schema<"CategoryTreeResponse">;

/**
 * The filters with how many books each choice would give (checklist step 198). The same panel is
 * the sidebar on wide screens and the drawer on phones.
 */
export function SearchFilters({
  state,
  facets,
  categories,
  onChange,
}: {
  state: SearchState;
  facets: Facets | undefined;
  categories: CategoryNode[];
  onChange: (changes: Partial<SearchState>) => void;
}) {
  const t = useTranslations("Search");
  const tLang = useTranslations("BookPage.languages");
  const tFormat = useTranslations("StaffCatalog.formats");
  const locale = useLocale();
  const [yearFrom, setYearFrom] = useState(state.yearFrom);
  const [yearTo, setYearTo] = useState(state.yearTo);

  // Category id -> its name in the page language
  const names = new Map<string, string>();
  const walk = (nodes: CategoryNode[]) =>
    nodes.forEach((n) => {
      names.set(n.id, locale === "si" ? n.nameSi : locale === "ta" ? n.nameTa : n.nameEn);
      walk(n.children);
    });
  walk(categories);

  const languageName = (code: string) =>
    ["si", "ta", "en"].includes(code) ? tLang(code as "si" | "ta" | "en") : code;
  const formatName = (code: string) => tFormat(code as "BOOK");
  const anyFilter =
    state.language ||
    state.format ||
    state.category ||
    state.available ||
    state.yearFrom ||
    state.yearTo;

  return (
    <div className="grid gap-6 text-sm">
      <label className="flex items-start gap-2">
        <input
          type="checkbox"
          className="mt-0.5 accent-primary"
          checked={state.available}
          onChange={(event) => onChange({ available: event.target.checked })}
        />
        <span>
          <span className="font-medium">{t("availableNow")}</span>
          {facets && (
            <span className="block text-xs text-muted-foreground">
              {t("availableCount", { count: facets.availableNow })}
            </span>
          )}
        </span>
      </label>

      <FacetGroup
        title={t("language")}
        values={facets?.languages ?? []}
        selected={state.language}
        label={languageName}
        onSelect={(value) => onChange({ language: value })}
      />
      <FacetGroup
        title={t("format")}
        values={facets?.formats ?? []}
        selected={state.format}
        label={formatName}
        onSelect={(value) => onChange({ format: value })}
      />
      <FacetGroup
        title={t("category")}
        values={facets?.categories ?? []}
        selected={state.category}
        label={(id) => names.get(id) ?? "…"}
        onSelect={(value) => onChange({ category: value })}
      />

      <fieldset className="grid gap-2">
        <legend className="mb-1 font-semibold">{t("years")}</legend>
        <form
          className="flex items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            onChange({ yearFrom, yearTo });
          }}
        >
          <label className="grid gap-1">
            <span className="text-xs text-muted-foreground">{t("yearFrom")}</span>
            <Input
              type="number"
              inputMode="numeric"
              min={1000}
              max={2100}
              className="w-20"
              value={yearFrom}
              onChange={(e) => setYearFrom(e.target.value)}
            />
          </label>
          <label className="grid gap-1">
            <span className="text-xs text-muted-foreground">{t("yearTo")}</span>
            <Input
              type="number"
              inputMode="numeric"
              min={1000}
              max={2100}
              className="w-20"
              value={yearTo}
              onChange={(e) => setYearTo(e.target.value)}
            />
          </label>
          <Button type="submit" size="sm" variant="secondary">
            {t("apply")}
          </Button>
        </form>
      </fieldset>

      {anyFilter && (
        <Button
          variant="ghost"
          size="sm"
          className="justify-self-start"
          onClick={() => {
            setYearFrom("");
            setYearTo("");
            onChange({
              language: "",
              format: "",
              category: "",
              available: false,
              yearFrom: "",
              yearTo: "",
            });
          }}
        >
          {t("clearAll")}
        </Button>
      )}
    </div>
  );
}

/** One facet: choose one value (click again to remove it). */
function FacetGroup({
  title,
  values,
  selected,
  label,
  onSelect,
}: {
  title: string;
  values: { value: string; count: number }[];
  selected: string;
  label: (value: string) => string;
  onSelect: (value: string) => void;
}) {
  if (values.length === 0 && !selected) return null;
  return (
    <fieldset>
      <legend className="mb-2 font-semibold">{title}</legend>
      <ul className="grid gap-1">
        {values.map(({ value, count }) => {
          const active = value === selected;
          return (
            <li key={value}>
              <button
                type="button"
                aria-pressed={active}
                onClick={() => onSelect(active ? "" : value)}
                className={cn(
                  "flex w-full items-center justify-between gap-2 rounded-md px-2 py-1 text-left hover:bg-muted",
                  active && "bg-primary/15 font-medium",
                )}
              >
                <span className="min-w-0 truncate">{label(value)}</span>
                <span className="text-xs text-muted-foreground">{count}</span>
              </button>
            </li>
          );
        })}
      </ul>
    </fieldset>
  );
}
