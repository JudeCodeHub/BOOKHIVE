"use client";

import { useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { ApplicationForm } from "@/features/membership/application-form";
import { MembershipStatus } from "@/features/membership/membership-status";
import { useMembership } from "@/features/membership/use-membership";

/**
 * /account/membership: the application form for people who haven't applied (or were rejected),
 * otherwise where the membership stands (MembershipStatus).
 */
export function MembershipPanel() {
  const t = useTranslations("Membership");
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const query = useMembership();

  if (query.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (query.isError) {
    return <FormAlert>{errorMessage(query.error)}</FormAlert>;
  }

  const membership = query.data;
  if (!membership || membership.status === "REJECTED") {
    return (
      <div className="grid gap-6">
        {membership?.status === "REJECTED" && (
          <div
            role="status"
            className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm"
          >
            <p className="font-medium">{t("rejected")}</p>
            {membership.statusReason && (
              <p>{t("rejectedReason", { reason: membership.statusReason })}</p>
            )}
            <p className="mt-1 text-muted-foreground">{t("applyAgain")}</p>
          </div>
        )}
        <p className="text-muted-foreground">{t("intro")}</p>
        <ApplicationForm />
      </div>
    );
  }

  return <MembershipStatus membership={membership} />;
}
