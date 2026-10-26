"use client";

import { useMemo, useState } from "react";
import { ChevronRight, X } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { Input } from "@/components/ui/input";
import type { Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type CategoryNode = Schema<"CategoryTreeResponse">;

/**
 * Choose categories from the Dewey tree (checklist step 175): search by name or DDC number,
 * expand and collapse branches, tick boxes; the choices show as removable chips on top.
 * While searching, matching categories are shown with their parents so the place in the tree
 * stays clear.
 */
export function CategoryTreePicker({
  tree,
  value,
  onChange,
  max = 10,
  labelledBy,
}: {
  tree: CategoryNode[];
  value: string[];
  onChange: (ids: string[]) => void;
  max?: number;
  /** id of the visible label, for screen readers */
  labelledBy?: string;
}) {
  const t = useTranslations("CategoryPicker");
  const locale = useLocale();
  const [query, setQuery] = useState("");
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const name = (node: CategoryNode) =>
    locale === "si" ? node.nameSi : locale === "ta" ? node.nameTa : node.nameEn;

  // id -> node, for showing the chosen ones as chips
  const byId = useMemo(() => {
    const map = new Map<string, CategoryNode>();
    const walk = (nodes: CategoryNode[]) =>
      nodes.forEach((node) => {
        map.set(node.id, node);
        walk(node.children);
      });
    walk(tree);
    return map;
  }, [tree]);

  const text = query.trim().toLowerCase();
  const matches = (node: CategoryNode) =>
    [node.nameEn, node.nameSi, node.nameTa, node.ddcCode ?? ""].some((s) =>
      s.toLowerCase().includes(text),
    );
  /** While searching: keep a node if it or anything below it matches. */
  const visible = (node: CategoryNode): boolean =>
    !text || matches(node) || node.children.some(visible);

  const toggle = (id: string, checked: boolean) =>
    onChange(checked ? [...value, id] : value.filter((v) => v !== id));
  const full = value.length >= max;

  function renderNodes(nodes: CategoryNode[], depth: number) {
    return (
      <ul>
        {nodes.filter(visible).map((node) => {
          const hasChildren = node.children.length > 0;
          // Searching opens every branch that leads to a match
          const open = hasChildren && (!!text || expanded.has(node.id));
          const checked = value.includes(node.id);
          return (
            <li key={node.id}>
              <div
                className="flex items-center gap-1 py-0.5"
                style={{ paddingInlineStart: `${depth * 1.25}rem` }}
              >
                {hasChildren ? (
                  <button
                    type="button"
                    className="rounded p-0.5 hover:bg-muted"
                    aria-expanded={open}
                    aria-label={
                      open ? t("collapse", { name: name(node) }) : t("expand", { name: name(node) })
                    }
                    onClick={() =>
                      setExpanded((set) => {
                        const next = new Set(set);
                        if (next.has(node.id)) next.delete(node.id);
                        else next.add(node.id);
                        return next;
                      })
                    }
                  >
                    <ChevronRight
                      aria-hidden
                      className={cn("size-4 transition-transform", open && "rotate-90")}
                    />
                  </button>
                ) : (
                  <span className="w-5" aria-hidden />
                )}
                <label className="flex cursor-pointer items-center gap-2">
                  <input
                    type="checkbox"
                    className="accent-primary"
                    checked={checked}
                    disabled={!checked && full}
                    onChange={(event) => toggle(node.id, event.target.checked)}
                  />
                  {node.ddcCode && (
                    <span className="font-mono text-xs text-muted-foreground">{node.ddcCode}</span>
                  )}
                  <span className={cn(text && matches(node) && "font-medium")}>{name(node)}</span>
                </label>
              </div>
              {open && renderNodes(node.children, depth + 1)}
            </li>
          );
        })}
      </ul>
    );
  }

  return (
    <div className="grid gap-2">
      {value.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label={t("chosen")}>
          {value.map((id) => {
            const node = byId.get(id);
            return (
              <li
                key={id}
                className="flex items-center gap-1 rounded-full bg-primary/15 py-0.5 pr-1 pl-2.5 text-xs"
              >
                {node ? name(node) : id}
                <button
                  type="button"
                  className="rounded-full p-0.5 hover:bg-primary/25"
                  aria-label={t("remove", { name: node ? name(node) : id })}
                  onClick={() => toggle(id, false)}
                >
                  <X aria-hidden className="size-3" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
      <Input
        type="search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder={t("search")}
        aria-label={t("search")}
      />
      <div
        role="group"
        aria-labelledby={labelledBy}
        className="max-h-64 overflow-y-auto rounded-lg border p-2 text-sm"
      >
        {tree.filter(visible).length === 0 ? (
          <p className="p-2 text-muted-foreground">{t("noMatch")}</p>
        ) : (
          renderNodes(tree, 0)
        )}
      </div>
      <p className="text-xs text-muted-foreground" aria-live="polite">
        {t("count", { count: value.length, max })}
      </p>
    </div>
  );
}
