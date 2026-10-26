"use client";

import { type ReactNode, useSyncExternalStore } from "react";
import { createPortal } from "react-dom";

const subscribe = () => () => {};

/**
 * Content that appears only on paper (e.g. a desk receipt). It's rendered straight into <body>,
 * and globals.css hides everything else while printing, so `window.print()` prints just this.
 */
export function PrintArea({ children }: { children: ReactNode }) {
  // document.body exists only in the browser: render nothing on the server
  const mounted = useSyncExternalStore(
    subscribe,
    () => true,
    () => false,
  );
  if (!mounted) return null;
  return createPortal(
    <div className="print-area hidden bg-white text-black print:block">{children}</div>,
    document.body,
  );
}
