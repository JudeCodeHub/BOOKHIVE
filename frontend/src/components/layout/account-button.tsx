"use client";

import { CircleUserRound } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { afterLoginPath } from "@/features/auth/redirects";
import { useSession } from "@/features/auth/use-session";
import { Link } from "@/i18n/navigation";

/**
 * Right side of the header: "Log in" for visitors, the user's first name (linking to their area)
 * once logged in. The full account menu and logout come in checklist steps 115-116.
 */
export function AccountButton() {
  const t = useTranslations("Header");
  const { status, user } = useSession();

  if (status === "loading") {
    // Same size as the real button, so the header doesn't jump when the session settles
    return <span aria-hidden className="h-8 w-20 animate-pulse rounded-lg bg-muted" />;
  }
  if (!user) {
    return (
      <Button asChild variant="secondary">
        <Link href="/login">{t("login")}</Link>
      </Button>
    );
  }
  return (
    <Button asChild variant="secondary">
      <Link href={afterLoginPath(user, null)}>
        <CircleUserRound aria-hidden />
        <span className="max-w-32 truncate">{user.fullName.split(" ")[0]}</span>
      </Link>
    </Button>
  );
}
