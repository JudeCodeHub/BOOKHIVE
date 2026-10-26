"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Page numbers with previous/next (checklist step 200): 1 … 4 5 6 … 20. `page` starts at 0,
 * like the backend; people see 1-based numbers.
 */
export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  const t = useTranslations("Search");
  if (totalPages <= 1) return null;

  // First, last, and two around the current page; gaps become "…"
  const shown = [...new Set([0, page - 2, page - 1, page, page + 1, page + 2, totalPages - 1])]
    .filter((p) => p >= 0 && p < totalPages)
    .sort((a, b) => a - b);

  return (
    <nav aria-label={t("pagination")} className="flex flex-wrap items-center justify-center gap-1">
      <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        <ChevronLeft aria-hidden />
        <span className="max-sm:sr-only">{t("previous")}</span>
      </Button>
      {shown.map((p, i) => (
        <span key={p} className="flex items-center gap-1">
          {i > 0 && p - shown[i - 1] > 1 && (
            <span aria-hidden className="px-1 text-muted-foreground">
              …
            </span>
          )}
          <Button
            variant={p === page ? "default" : "ghost"}
            size="sm"
            aria-label={t("goToPage", { page: p + 1 })}
            aria-current={p === page ? "page" : undefined}
            onClick={() => onChange(p)}
            className={cn("min-w-8")}
          >
            {p + 1}
          </Button>
        </span>
      ))}
      <Button
        variant="ghost"
        size="sm"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        <span className="max-sm:sr-only">{t("next")}</span>
        <ChevronRight aria-hidden />
      </Button>
    </nav>
  );
}
