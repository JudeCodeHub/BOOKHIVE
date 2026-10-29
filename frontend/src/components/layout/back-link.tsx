import type { ComponentProps, ReactNode } from "react";
import { ArrowLeft } from "lucide-react";
import { Link } from "@/i18n/navigation";

/** "← All events" above a staff page: back to the list it was opened from. */
export function BackLink({
  href,
  children,
}: {
  href: ComponentProps<typeof Link>["href"];
  children: ReactNode;
}) {
  return (
    <Link
      href={href}
      className="inline-flex items-center gap-1 justify-self-start text-sm text-muted-foreground underline-offset-4 hover:underline"
    >
      <ArrowLeft aria-hidden className="size-4" />
      {children}
    </Link>
  );
}
