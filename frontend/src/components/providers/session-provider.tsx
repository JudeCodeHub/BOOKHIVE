"use client";

import { useEffect, type ReactNode } from "react";
import { restoreSession } from "@/features/auth/session";

/**
 * Restores the login session once when the app opens (the access token lives only in memory, so a
 * reload loses it; the refresh cookie gets a new one). Does nothing for visitors who never logged
 * in, so they don't cause a pointless 401.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    void restoreSession();
  }, []);

  return children;
}
