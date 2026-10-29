"use client";

import { useSyncExternalStore } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import {
  DEFAULT_TEXT_SIZE,
  TEXT_SIZES,
  applyTextSize,
  readTextSize,
  storeTextSize,
  type TextSize,
} from "@/lib/text-size";
import { cn } from "@/lib/utils";

const listeners = new Set<() => void>();
let current: TextSize | null = null;

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function getSize(): TextSize {
  current ??= readTextSize();
  return current;
}

/** The server can't know the reader's choice; the browser settles on it right after loading. */
function getServerSize(): TextSize {
  return DEFAULT_TEXT_SIZE;
}

function setSize(size: TextSize) {
  current = size;
  applyTextSize(size);
  storeTextSize(size);
  listeners.forEach((listener) => listener());
}

/**
 * A− / A / A+ (checklist step 383): makes everything on the page bigger or smaller for people who
 * find the default hard to read. The choice is remembered on this device and applies to every
 * page, including the staff and admin consoles.
 */
export function TextSizeToggle({ className }: { className?: string }) {
  const t = useTranslations("TextSize");
  const size = useSyncExternalStore(subscribe, getSize, getServerSize);
  const index = TEXT_SIZES.indexOf(size);

  const step = (by: number) => {
    const next = TEXT_SIZES[Math.min(TEXT_SIZES.length - 1, Math.max(0, index + by))];
    if (next !== size) setSize(next);
  };

  return (
    <div
      role="group"
      aria-label={t("label")}
      className={cn("flex items-center rounded-lg", className)}
    >
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="text-inherit"
        aria-label={t("smaller")}
        title={t("smaller")}
        disabled={index === 0}
        onClick={() => step(-1)}
      >
        <span aria-hidden className="text-xs font-semibold">
          A−
        </span>
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="text-inherit"
        aria-label={t("reset")}
        title={t("reset")}
        disabled={size === DEFAULT_TEXT_SIZE}
        onClick={() => setSize(DEFAULT_TEXT_SIZE)}
      >
        <span aria-hidden className="text-sm font-semibold">
          A
        </span>
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="text-inherit"
        aria-label={t("bigger")}
        title={t("bigger")}
        disabled={index === TEXT_SIZES.length - 1}
        onClick={() => step(1)}
      >
        <span aria-hidden className="text-base font-semibold">
          A+
        </span>
      </Button>
      {/* Tells a screen reader what changed, since the buttons' own labels stay the same */}
      <span role="status" aria-live="polite" className="sr-only">
        {t(`sizes.${size}`)}
      </span>
    </div>
  );
}
