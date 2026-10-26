"use client";

import { type Ref, useEffect, useImperativeHandle, useRef, useState } from "react";
import { ScanBarcode } from "lucide-react";
import { cn } from "@/lib/utils";

export type ScanInputHandle = { focus: () => void };

/** Same code twice within this time = one scan (some scanners double-fire). */
const DOUBLE_SCAN_MS = 800;

/**
 * The big scan box of desk mode (checklist step 236). A USB barcode scanner "types" the code and
 * presses Enter, so this is a plain text box that:
 *
 * - is focused on load, after every scan, and when the window gets focus back, so staff never
 *   have to click before scanning;
 * - hands over the code upper-cased and trimmed, then clears itself for the next one;
 * - ignores an accidental double scan of the same code.
 *
 * Typing a code by hand and pressing Enter works the same way (damaged labels).
 */
export function ScanInput({
  ref,
  id,
  label,
  hint,
  placeholder,
  onScan,
  busy = false,
  className,
}: {
  ref?: Ref<ScanInputHandle>;
  id: string;
  label: string;
  hint?: string;
  placeholder?: string;
  onScan: (code: string) => void;
  /** While the last scan is being processed: scanning stays possible, the box shows it's busy */
  busy?: boolean;
  className?: string;
}) {
  const input = useRef<HTMLInputElement>(null);
  const last = useRef<{ code: string; at: number } | null>(null);
  const [value, setValue] = useState("");

  useImperativeHandle(ref, () => ({ focus: () => input.current?.focus() }), []);

  useEffect(() => {
    // Back from another window or tab: ready to scan again
    const refocus = () => input.current?.focus();
    window.addEventListener("focus", refocus);
    return () => window.removeEventListener("focus", refocus);
  }, []);

  return (
    <form
      className={cn("grid gap-2", className)}
      onSubmit={(event) => {
        event.preventDefault();
        const code = value.trim().toUpperCase();
        setValue("");
        input.current?.focus();
        if (!code) return;
        const now = Date.now();
        if (last.current?.code === code && now - last.current.at < DOUBLE_SCAN_MS) return;
        last.current = { code, at: now };
        onScan(code);
      }}
    >
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <div className="relative">
        <ScanBarcode
          aria-hidden
          className={cn(
            "pointer-events-none absolute top-1/2 left-4 size-7 -translate-y-1/2 text-muted-foreground",
            busy && "animate-pulse text-primary",
          )}
        />
        <input
          ref={input}
          id={id}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder={placeholder}
          aria-describedby={hint ? `${id}-hint` : undefined}
          aria-busy={busy}
          // A scanner is a keyboard: no suggestions, corrections or capitals getting in the way
          autoFocus
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="characters"
          spellCheck={false}
          enterKeyHint="go"
          className="h-16 w-full rounded-xl border-2 border-input bg-background pr-4 pl-14 font-mono text-2xl tracking-wider uppercase transition-colors outline-none placeholder:font-sans placeholder:text-base placeholder:tracking-normal placeholder:normal-case focus-visible:border-primary focus-visible:ring-4 focus-visible:ring-ring/40"
        />
      </div>
      {hint && (
        <p id={`${id}-hint`} className="text-sm text-muted-foreground">
          {hint}
        </p>
      )}
    </form>
  );
}
