import { cn } from "@/lib/utils";

/**
 * Placeholder BookHive mark: a honeycomb hexagon with an open book (PRD §11).
 * Colours are fixed (not theme tokens) so the brand looks the same in light and dark mode.
 */
export function LogoMark({ className, title }: { className?: string; title?: string }) {
  return (
    <svg
      viewBox="0 0 32 32"
      className={cn("size-8 shrink-0", className)}
      role={title ? "img" : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
    >
      {/* Pointy-top hexagon */}
      <path d="M16 1 29 8.5v15L16 31 3 23.5v-15Z" fill="#F59E0B" />
      {/* Open book: two pages meeting at the spine */}
      <path
        d="M16 11.5c-2.6-1.5-5.4-1.7-8-.8v10.8c2.6-.9 5.4-.7 8 .8 2.6-1.5 5.4-1.7 8-.8V10.7c-2.6-.9-5.4-.7-8 .8Zm0 0v10.8"
        fill="none"
        stroke="#1F2937"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/** Mark + "BookHive" wordmark, used in headers. */
export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-2", className)}>
      <LogoMark />
      <span className="font-heading text-xl font-semibold tracking-tight">BookHive</span>
    </span>
  );
}
