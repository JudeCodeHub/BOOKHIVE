"use client";

import { createContext, use, type ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * A row of choices that filters what's below it: "All / Due soon / Overdue", the report tabs, the
 * desk's modes. It looks like the tabs, but it isn't a tab set: there are no panels, the choice
 * usually changes the address and reloads a list. So each choice is a pressed-or-not button,
 * which is what a screen reader should announce (checklist step 384; tabs here claimed panels
 * that don't exist).
 */
const SegmentedContext = createContext<{
  value: string;
  onValueChange: (value: string) => void;
} | null>(null);

export function Segmented({
  value,
  onValueChange,
  label,
  className,
  children,
}: {
  value: string;
  onValueChange: (value: string) => void;
  /** What is being filtered, e.g. "Loans to show" */
  label: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <SegmentedContext value={{ value, onValueChange }}>
      <div className="group/tabs flex gap-2 data-horizontal:flex-col" data-orientation="horizontal">
        <div
          role="group"
          aria-label={label}
          data-variant="default"
          className={cn("group/tabs-list inline-flex w-fit items-center justify-center rounded-lg p-[3px] text-muted-foreground group-data-horizontal/tabs:h-8 group-data-vertical/tabs:h-fit group-data-vertical/tabs:flex-col data-[variant=line]:rounded-none", className)}
        >
          {children}
        </div>
      </div>
    </SegmentedContext>
  );
}

export function SegmentedItem({
  value,
  className,
  children,
}: {
  value: string;
  className?: string;
  children: ReactNode;
}) {
  const context = use(SegmentedContext);
  if (!context) throw new Error("SegmentedItem must be inside a Segmented");
  const active = context.value === value;
  return (
    <button
      type="button"
      aria-pressed={active}
      data-active={active || undefined}
      data-slot="tabs-trigger"
      className={cn("relative inline-flex h-[calc(100%-1px)] flex-1 items-center justify-center gap-1.5 rounded-md border border-transparent px-1.5 py-0.5 text-sm font-medium whitespace-nowrap text-foreground/70 transition-all group-data-vertical/tabs:w-full group-data-vertical/tabs:justify-start hover:text-foreground focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-1 focus-visible:outline-ring disabled:pointer-events-none disabled:opacity-50 has-data-[icon=inline-end]:pr-1 has-data-[icon=inline-start]:pl-1 dark:text-muted-foreground dark:hover:text-foreground group-data-[variant=default]/tabs-list:data-active:shadow-sm group-data-[variant=line]/tabs-list:data-active:shadow-none [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4", className)}
      onClick={() => context.onValueChange(value)}
    >
      {children}
    </button>
  );
}
