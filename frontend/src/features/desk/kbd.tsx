import type { ReactNode } from "react";

/** A key name shown next to the action it triggers, e.g. F2 (desk shortcuts, step 241). */
export function Kbd({ children }: { children: ReactNode }) {
  return (
    <kbd className="rounded border bg-muted px-1 font-mono text-[0.7rem] font-normal text-muted-foreground">
      {children}
    </kbd>
  );
}
