"use client";

import { useState } from "react";
import { CircleAlert, CircleCheck, TriangleAlert, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { MemberStatusBadge } from "@/features/staff/member-status-badge";
import { Link } from "@/i18n/navigation";
import type { Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type MemberCardSummary = Schema<"MemberCardSummary">;
export type DeskProblem = Schema<"ProblemSummary">;

/** Warn at the desk when the membership ends within this many days. */
const EXPIRY_WARNING_DAYS = 30;

/**
 * The member at the desk (checklist step 237): who they are, how many books they have, and every
 * alert at once. Red = can't borrow; amber = an admin may override with a reason (step 246).
 */
export function MemberCard({
  member,
  onClear,
}: {
  member: MemberCardSummary;
  onClear: () => void;
}) {
  const t = useTranslations("Desk");
  const format = useFormatter();
  // "Now" once per card: the card is replaced for every member scanned
  const [now] = useState(() => Date.now());
  const daysLeft = Math.ceil((new Date(member.expiresAt).getTime() - now) / (24 * 60 * 60 * 1000));
  const expiringSoon = daysLeft >= 0 && daysLeft <= EXPIRY_WARNING_DAYS;
  const atLimit = member.itemsOut >= member.maxItems;

  return (
    <section
      aria-labelledby="desk-member-name"
      className="grid gap-4 rounded-xl border bg-card p-4 sm:p-5"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="grid gap-1">
          <h2 id="desk-member-name" className="text-lg font-semibold">
            <Link href={`/staff/members/${member.memberId}`} className="hover:underline">
              {member.fullName}
            </Link>
          </h2>
          <p className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <span className="font-mono">{member.memberNo}</span>
            <MemberStatusBadge status={member.status} />
          </p>
        </div>
        <Button variant="ghost" size="sm" onClick={onClear}>
          <X aria-hidden />
          {t("nextMember")}
        </Button>
      </div>

      <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-muted-foreground">{t("itemsOut")}</dt>
          <dd className={cn("text-base font-medium", atLimit && "text-overdue-foreground")}>
            {t("itemsOutValue", { out: member.itemsOut, max: member.maxItems })}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t("loanPeriod")}</dt>
          <dd className="text-base font-medium">{t("days", { count: member.loanDays })}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t("validUntil")}</dt>
          <dd className={cn("text-base font-medium", expiringSoon && "text-on-loan-foreground")}>
            {format.dateTime(new Date(member.expiresAt), { dateStyle: "medium" })}
          </dd>
        </div>
      </dl>

      <ul className="grid gap-2" aria-label={t("alerts")}>
        {member.problems.map((problem) => (
          <ProblemAlert key={problem.reason} problem={problem} />
        ))}
        {expiringSoon && member.problems.length === 0 && (
          <li className="flex items-start gap-2 rounded-lg border border-on-loan/40 bg-on-loan/15 px-3 py-2 text-sm">
            <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-on-loan-foreground" />
            {t("expiresSoon", { count: daysLeft })}
          </li>
        )}
        {member.problems.length === 0 && (
          <li className="flex items-center gap-2 rounded-lg border border-available/40 bg-available/15 px-3 py-2 text-sm">
            <CircleCheck aria-hidden className="size-4 shrink-0 text-available-foreground" />
            {atLimit
              ? t("atLimit")
              : t("readyToBorrow", { count: member.maxItems - member.itemsOut })}
          </li>
        )}
      </ul>
    </section>
  );
}

/** One reason, in the desk's language (the server's text is English only). */
export function ProblemAlert({ problem }: { problem: DeskProblem }) {
  const t = useTranslations("Desk");
  const Icon = problem.overridable ? TriangleAlert : CircleAlert;
  return (
    <li
      className={cn(
        "flex items-start gap-2 rounded-lg border px-3 py-2 text-sm",
        problem.overridable
          ? "border-on-loan/40 bg-on-loan/15"
          : "border-destructive/30 bg-destructive/10 text-destructive",
      )}
    >
      <Icon
        aria-hidden
        className={cn("mt-0.5 size-4 shrink-0", problem.overridable && "text-on-loan-foreground")}
      />
      <span>
        {t(`problems.${problem.reason}`)}
        {problem.overridable && (
          <span className="text-muted-foreground"> · {t("overridable")}</span>
        )}
      </span>
    </li>
  );
}
