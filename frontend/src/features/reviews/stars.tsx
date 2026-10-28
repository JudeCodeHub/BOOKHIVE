"use client";

import { Star } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";

/** A rating shown as 5 stars (halves round to the nearest whole star); read out as "4 of 5". */
export function Stars({ rating, className }: { rating: number; className?: string }) {
  const t = useTranslations("Reviews");
  const filled = Math.round(rating);
  return (
    <span
      role="img"
      aria-label={t("starsLabel", { rating })}
      className={cn("inline-flex gap-0.5", className)}
    >
      {[1, 2, 3, 4, 5].map((n) => (
        <Star
          key={n}
          aria-hidden
          className={cn(
            "size-4",
            n <= filled ? "fill-amber-400 text-amber-400" : "text-muted-foreground/40",
          )}
        />
      ))}
    </span>
  );
}

/**
 * Choosing 1–5 stars: a radio group, so it works with the keyboard (arrow keys) and screen
 * readers ("3 stars, 3 of 5").
 */
export function StarInput({
  value,
  onChange,
  label,
  invalid,
}: {
  value: number;
  onChange: (rating: number) => void;
  label: string;
  invalid?: boolean;
}) {
  const t = useTranslations("Reviews");
  return (
    <fieldset aria-invalid={invalid || undefined}>
      <legend className="mb-1 text-sm font-medium">{label}</legend>
      <div className="flex gap-1">
        {[1, 2, 3, 4, 5].map((n) => (
          <label
            key={n}
            className="cursor-pointer rounded-md has-focus-visible:ring-3 has-focus-visible:ring-ring/50"
          >
            <input
              type="radio"
              name="rating"
              value={n}
              checked={value === n}
              onChange={() => onChange(n)}
              className="sr-only"
            />
            <span className="sr-only">{t("starChoice", { count: n })}</span>
            <Star
              aria-hidden
              className={cn(
                "size-7 transition-colors",
                n <= value ? "fill-amber-400 text-amber-400" : "text-muted-foreground/50",
              )}
            />
          </label>
        ))}
      </div>
    </fieldset>
  );
}
