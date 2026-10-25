"use client";

import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";

/** Previous / next for a paged list. `page` starts at 0, like the backend. */
export function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  const t = useTranslations("StaffApplications");
  if (totalPages <= 1) return null;

  return (
    <nav
      aria-label={t("page", { page: page + 1, total: totalPages })}
      className="flex items-center gap-3"
    >
      <Button
        variant="secondary"
        size="sm"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
      >
        {t("previous")}
      </Button>
      <span className="text-sm text-muted-foreground">
        {t("page", { page: page + 1, total: totalPages })}
      </span>
      <Button
        variant="secondary"
        size="sm"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        {t("next")}
      </Button>
    </nav>
  );
}
