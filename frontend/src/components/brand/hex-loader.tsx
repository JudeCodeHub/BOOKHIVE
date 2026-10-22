import { cn } from "@/lib/utils";

/**
 * Loading indicator: three honeycomb cells pulsing one after another.
 * Screen readers announce the label; users who turn off animations see static cells.
 */
export function HexLoader({
  label = "Loading",
  className,
}: {
  label?: string;
  className?: string;
}) {
  return (
    <div role="status" className={cn("inline-flex items-center gap-1", className)}>
      {[0, 150, 300].map((delay) => (
        <svg
          key={delay}
          viewBox="0 0 32 32"
          aria-hidden
          className="size-4 animate-pulse fill-primary motion-reduce:animate-none"
          style={{ animationDelay: `${delay}ms` }}
        >
          <path d="M16 1 29 8.5v15L16 31 3 23.5v-15Z" />
        </svg>
      ))}
      <span className="sr-only">{label}</span>
    </div>
  );
}
