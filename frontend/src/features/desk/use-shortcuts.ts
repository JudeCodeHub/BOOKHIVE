"use client";

import { useEffect, useEffectEvent } from "react";

/**
 * Desk keyboard shortcuts (checklist step 241), by `KeyboardEvent.key`: "F2", "Escape"...
 * Function keys and Escape work even while the cursor is in the scan box, which it almost
 * always is. A handler returns false to let the key through (e.g. nothing to confirm yet).
 */
export function useShortcuts(handlers: Record<string, () => boolean | void>) {
  const onKeyDown = useEffectEvent((event: KeyboardEvent) => {
    if (event.altKey || event.ctrlKey || event.metaKey || event.repeat) return;
    const handler = handlers[event.key];
    if (handler && handler() !== false) {
      // F-keys have browser meanings too (F3 = find): ours win on the desk page
      event.preventDefault();
    }
  });

  useEffect(() => {
    const listener = (event: KeyboardEvent) => onKeyDown(event);
    window.addEventListener("keydown", listener);
    return () => window.removeEventListener("keydown", listener);
  }, []);
}
