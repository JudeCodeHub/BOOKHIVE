import { useFormatter } from "next-intl";
import { cn } from "@/lib/utils";

/**
 * A big day and short month ("20 NOV") in place of a picture: on event cards without a poster
 * (large), and in compact event lists (small). Hidden from screen readers: the date is also
 * written out next to it.
 */
export function DateTile({
  date,
  size = "small",
  className,
}: {
  date: Date;
  size?: "large" | "small";
  className?: string;
}) {
  const format = useFormatter();
  const large = size === "large";
  return (
    <div
      aria-hidden
      className={cn(
        "flex items-center justify-center bg-primary/10 text-primary",
        large ? "gap-2 p-4 sm:flex-col" : "flex-col rounded-md py-2",
        className,
      )}
    >
      <span
        className={cn("font-heading leading-none font-semibold", large ? "text-4xl" : "text-2xl")}
      >
        {format.dateTime(date, { day: "numeric" })}
      </span>
      <span className={cn("font-medium uppercase", large ? "text-sm" : "text-xs")}>
        {format.dateTime(date, { month: "short" })}
      </span>
    </div>
  );
}
