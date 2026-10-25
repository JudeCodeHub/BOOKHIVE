"use client";

import { useEffect, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { onSessionEnded, restoreSession } from "@/features/auth/session";

/**
 * Restores the login session once when the app opens (the access token lives only in memory, so a
 * reload loses it; the refresh cookie gets a new one). Does nothing for visitors who never logged
 * in, so they don't cause a pointless 401.
 *
 * Also tells the user when they've been logged out without pressing "Log out" here.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const t = useTranslations("Auth.session");

  useEffect(() => {
    void restoreSession();
  }, []);

  useEffect(
    () =>
      onSessionEnded((reason) => {
        toast.info(reason === "expired" ? t("expired") : t("otherTab"));
      }),
    [t],
  );

  return children;
}
