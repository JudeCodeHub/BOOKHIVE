"use client";

import { useEffect } from "react";
import { TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";

/** The props Next.js passes to an error.tsx boundary (Next 16.3+: `error` is unknown). */
export type RouteErrorProps = { error: unknown; retry: () => void };

/**
 * Shown by every area's error.tsx when a page crashes. Never shows the raw error text (it can
 * contain internals); instead shows a reference the user can quote: the backend X-Request-Id for
 * API errors, or Next.js's error digest for server errors (both match our logs).
 */
export function ErrorState({ error, retry }: RouteErrorProps) {
  const t = useTranslations("Error");

  useEffect(() => {
    console.error(error);
  }, [error]);

  const reference =
    error instanceof ApiError
      ? error.requestId
      : error instanceof Error && "digest" in error
        ? String(error.digest)
        : undefined;

  return (
    <div
      role="alert"
      className="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center"
    >
      <TriangleAlert aria-hidden className="size-10 text-destructive" />
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <p className="max-w-md text-muted-foreground">{t("description")}</p>
      {reference && (
        <p className="font-mono text-xs text-muted-foreground">
          {t("reference", { id: reference })}
        </p>
      )}
      {/* Re-fetches and re-renders the failed part of the page */}
      <Button onClick={() => retry()} className="mt-2">
        {t("retry")}
      </Button>
    </div>
  );
}
