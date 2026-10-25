"use client";

import { useTranslations } from "next-intl";
import { Suspense, useEffect, type ReactNode } from "react";
import { HexLoader } from "@/components/brand/hex-loader";
import { afterLoginPath } from "@/features/auth/redirects";
import { canOpen, type Area } from "@/features/auth/session-hint";
import { useSession } from "@/features/auth/use-session";
import { usePathname, useRouter } from "@/i18n/navigation";

/**
 * Second half of the route guard (checklist step 114). The proxy only sees the hint cookie; this
 * checks the real session once it's restored, and covers the cases the proxy can't (session
 * expired while the tab was open, logout in another tab).
 *
 * Shows a loader until the session is known, so private content never flashes for a visitor.
 */
export function RequireArea(props: { area: Area; children: ReactNode }) {
  // It reads the current URL (for ?next=), which pages with an [id] only know in the browser
  return (
    <Suspense fallback={<AreaLoader />}>
      <Guard {...props} />
    </Suspense>
  );
}

function AreaLoader() {
  const t = useTranslations("Common");
  return (
    <div className="flex flex-1 items-center justify-center p-16">
      <HexLoader label={t("loading")} />
    </div>
  );
}

function Guard({ area, children }: { area: Area; children: ReactNode }) {
  const { status, user } = useSession();
  const router = useRouter();
  const pathname = usePathname();
  const allowed = !!user && canOpen(area, user.roles);

  useEffect(() => {
    if (status === "anonymous") {
      const next = pathname + window.location.search;
      router.replace(`/login?next=${encodeURIComponent(next)}`);
    } else if (user && !allowed) {
      router.replace(afterLoginPath(user, null));
    }
  }, [status, user, allowed, pathname, router]);

  if (!allowed) {
    return <AreaLoader />;
  }
  return children;
}
