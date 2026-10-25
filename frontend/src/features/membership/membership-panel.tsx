"use client";

import { useFormatter, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { ApplicationForm } from "@/features/membership/application-form";
import { useMembership } from "@/features/membership/use-membership";

/**
 * /account/membership: the application form for people who haven't applied (or were rejected),
 * otherwise a short summary. The full status page with timeline comes in checklist step 140.
 */
export function MembershipPanel() {
  const t = useTranslations("Membership");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
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

  return (
    <div role="status" className="grid gap-1 rounded-lg border p-4">
      <p className="font-medium">{t(`status.${membership.status}`)}</p>
      {membership.memberNo && <p>{t("status.memberNo", { memberNo: membership.memberNo })}</p>}
      {membership.expiresAt && (
        <p className="text-muted-foreground">
          {t("status.expires", {
            date: format.dateTime(new Date(membership.expiresAt), { dateStyle: "long" }),
          })}
        </p>
      )}
    </div>
  );
}
