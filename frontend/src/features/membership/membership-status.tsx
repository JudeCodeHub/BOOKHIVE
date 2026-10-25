"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Circle, CircleCheck, CircleDot, IdCard } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { MEMBERSHIP_QUERY_KEY, type Membership } from "@/features/membership/use-membership";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type MembershipType = Schema<"MembershipTypeResponse">;
type Renewal = Schema<"RenewalResponse">;

/** Same as MembershipService.RENEWAL_WINDOW_DAYS on the backend. */
const RENEWAL_WINDOW_DAYS = 30;
const DAY_MS = 24 * 60 * 60 * 1000;

/** Whole days from today until an ISO date (negative once it's past). */
function daysUntil(isoDate: string) {
  const [year, month, day] = isoDate.split("-").map(Number);
  const end = Date.UTC(year, month - 1, day);
  const now = new Date();
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((end - today) / DAY_MS);
}

/**
 * Where the membership stands (checklist step 140): a timeline while the application is being
 * reviewed; once approved, the member number, expiry and renewal.
 */
export function MembershipStatus({ membership }: { membership: Membership }) {
  const t = useTranslations("Membership.statusPage");
  const tStatus = useTranslations("Membership.status");
  const tErrors = useTranslations("Membership.errors");
  const format = useFormatter();
  const locale = useLocale();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const types = useQuery({
    queryKey: ["membership-types"],
    queryFn: () => api.get<MembershipType[]>("/memberships/types"),
  });

  const type = types.data?.find((item) => item.code === membership.membershipType);
  const typeName = !type
    ? membership.membershipType
    : locale === "si"
      ? type.nameSi
      : locale === "ta"
        ? type.nameTa
        : type.nameEn;
  const longDate = (iso: string) => format.dateTime(new Date(iso), { dateStyle: "long" });

  const renew = useMutation({
    mutationFn: () => api.post<Renewal>("/me/membership/renew"),
    onSuccess: async (renewal) => {
      toast.success(t("renewed", { date: longDate(renewal.expiresAt) }));
      if (renewal.feeDue > 0) toast.info(t("feeDue", { fee: renewal.feeDue }));
      await queryClient.invalidateQueries({ queryKey: MEMBERSHIP_QUERY_KEY });
    },
    onError: (error) => {
      const code = error instanceof ApiError ? error.code : "";
      toast.error(tErrors.has(code as never) ? tErrors(code as never) : errorMessage(error));
    },
  });

  if (membership.status === "PENDING") {
    return (
      <section aria-labelledby="timeline" className="grid gap-4 rounded-lg border p-4">
        <h2 id="timeline" className="font-medium">
          {t("timeline")}
        </h2>
        <ol className="grid gap-4">
          <TimelineStep state="done" title={t("applied")}>
            {longDate(membership.appliedAt)}
          </TimelineStep>
          <TimelineStep state="current" title={t("review")}>
            {t("reviewWaiting")}
          </TimelineStep>
          <TimelineStep state="todo" title={t("decision")} />
        </ol>
      </section>
    );
  }

  const daysLeft = membership.expiresAt ? daysUntil(membership.expiresAt) : null;
  const canRenew =
    (membership.status === "ACTIVE" || membership.status === "EXPIRED") &&
    daysLeft !== null &&
    daysLeft <= RENEWAL_WINDOW_DAYS;

  return (
    <section className="grid gap-4 rounded-lg border p-4">
      <p className="font-medium" role="status">
        {tStatus(membership.status)}
      </p>

      <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[auto_1fr]">
        <dt className="text-muted-foreground">{t("type")}</dt>
        <dd className="font-medium">{typeName}</dd>
        {membership.memberNo && (
          <>
            <dt className="text-muted-foreground">{t("memberNo")}</dt>
            <dd className="font-mono font-medium">{membership.memberNo}</dd>
          </>
        )}
        {membership.expiresAt && (
          <>
            <dt className="text-muted-foreground">{t("validUntil")}</dt>
            <dd className="font-medium">
              {longDate(membership.expiresAt)}
              {daysLeft !== null && daysLeft >= 0 && (
                <span
                  className={cn(
                    "ml-2 font-normal",
                    daysLeft <= RENEWAL_WINDOW_DAYS ? "text-destructive" : "text-muted-foreground",
                  )}
                >
                  ({t("daysLeft", { days: daysLeft })})
                </span>
              )}
            </dd>
          </>
        )}
        {membership.statusReason && (
          <>
            <dt className="text-muted-foreground">{t("reason")}</dt>
            <dd>{membership.statusReason}</dd>
          </>
        )}
      </dl>

      {membership.status === "SUSPENDED" && (
        <p className="text-sm text-muted-foreground">{t("suspendedHelp")}</p>
      )}
      {membership.status === "EXPIRED" && (
        <p className="text-sm text-muted-foreground">{t("expiredHelp")}</p>
      )}

      <div className="flex flex-wrap gap-3">
        {membership.status === "ACTIVE" && (
          <Button asChild variant="secondary">
            <Link href="/account/card">
              <IdCard aria-hidden />
              {t("viewCard")}
            </Link>
          </Button>
        )}
        {canRenew && (
          <Button onClick={() => renew.mutate()} disabled={renew.isPending}>
            {renew.isPending ? t("renewing") : t("renew")}
          </Button>
        )}
      </div>
      {membership.status === "ACTIVE" && !canRenew && membership.expiresAt && (
        <p className="text-sm text-muted-foreground">
          {t("renewOpens", {
            date: longDate(
              new Date(
                new Date(membership.expiresAt).getTime() - RENEWAL_WINDOW_DAYS * DAY_MS,
              ).toISOString(),
            ),
          })}
        </p>
      )}
    </section>
  );
}

function TimelineStep({
  state,
  title,
  children,
}: {
  state: "done" | "current" | "todo";
  title: string;
  children?: ReactNode;
}) {
  const Icon = state === "done" ? CircleCheck : state === "current" ? CircleDot : Circle;
  return (
    <li className="flex gap-3" aria-current={state === "current" ? "step" : undefined}>
      <Icon
        aria-hidden
        className={cn(
          "mt-0.5 size-5 shrink-0",
          state === "done" && "text-primary",
          state === "current" && "animate-pulse text-primary motion-reduce:animate-none",
          state === "todo" && "text-muted-foreground/40",
        )}
      />
      <div className="text-sm">
        <p className={cn("font-medium", state === "todo" && "text-muted-foreground")}>{title}</p>
        {children && <p className="text-muted-foreground">{children}</p>}
      </div>
    </li>
  );
}
