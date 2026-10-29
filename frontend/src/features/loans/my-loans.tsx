"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CircleAlert, RefreshCw } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { BookCover } from "@/features/catalog/book-cover";
import { type RenewalCheck, refusedRenewal } from "@/features/loans/renewal-reasons";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";

type LoanSummary = Schema<"LoanSummary">;
type Renewal = Schema<"LoanRenewalResponse">;

/** Amber "due soon" from this many days before the due date. */
const DUE_SOON_DAYS = 3;
const DAY_MS = 24 * 60 * 60 * 1000;

/** Whole days from today to a "2026-11-09" date (negative = in the past). */
function daysUntil(isoDate: string, now: number) {
  const [y, m, d] = isoDate.split("-").map(Number);
  const today = new Date(now);
  return Math.round(
    (Date.UTC(y, m - 1, d) - Date.UTC(today.getFullYear(), today.getMonth(), today.getDate())) /
      DAY_MS,
  );
}

/**
 * "My loans" (checklist step 244): the books I have, soonest due first, each with a badge for
 * how soon it's due and a Renew button. If renewing isn't allowed, the reasons appear right under
 * that book (e.g. someone has reserved it).
 */
export function MyLoans() {
  const t = useTranslations("MyLoans");
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const loans = useQuery({
    queryKey: ["me", "loans"],
    queryFn: () => api.get<LoanSummary[]>("/me/loans"),
  });

  if (loans.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (loans.isError) {
    if (loans.error instanceof ApiError && loans.error.code === "MEMBER_NOT_FOUND") {
      return (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">
          {t.rich("notMember", {
            link: (chunks) => (
              <Link href="/account/membership" className="font-medium text-link underline">
                {chunks}
              </Link>
            ),
          })}
        </p>
      );
    }
    return <FormAlert>{errorMessage(loans.error)}</FormAlert>;
  }
  if (loans.data.length === 0) {
    return (
      <p className="rounded-lg border p-6 text-center text-muted-foreground">
        {t.rich("empty", {
          link: (chunks) => (
            <Link href="/search" className="font-medium text-link underline">
              {chunks}
            </Link>
          ),
        })}
      </p>
    );
  }

  const overdue = loans.data.filter((loan) => loan.overdue).length;
  return (
    <div className="grid gap-4">
      <p className="text-sm text-muted-foreground">
        {t("summary", { count: loans.data.length })}
        {overdue > 0 && (
          <span className="font-medium text-overdue-foreground">
            {" "}
            {t("summaryOverdue", { count: overdue })}
          </span>
        )}
      </p>
      <ul className="grid gap-3">
        {loans.data.map((loan) => (
          <LoanCard key={loan.loanId} loan={loan} />
        ))}
      </ul>
    </div>
  );
}

function LoanCard({ loan }: { loan: LoanSummary }) {
  const t = useTranslations("MyLoans");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [now] = useState(() => Date.now());
  const [refused, setRefused] = useState<RenewalCheck | null>(null);

  const renew = useMutation({
    mutationFn: () => api.post<Renewal>(`/me/loans/${loan.loanId}/renew`),
    onSuccess: (renewal) => {
      setRefused(null);
      toast.success(
        t("renewed", {
          title: renewal.title,
          date: format.dateTime(new Date(renewal.dueDate), { dateStyle: "long" }),
        }),
      );
      void queryClient.invalidateQueries({ queryKey: ["me", "loans"] });
    },
    onError: (error) => {
      const reasons = refusedRenewal(error);
      if (reasons) setRefused(reasons);
      else toast.error(errorMessage(error));
    },
  });

  const lost = loan.status === "LOST";
  const days = daysUntil(loan.dueDate, now);
  const dueDate = format.dateTime(new Date(loan.dueDate), {
    weekday: "short",
    day: "numeric",
    month: "long",
  });

  return (
    <li className="flex gap-4 rounded-xl border bg-card p-3 sm:p-4">
      <Link href={`/books/${loan.bookId}`} className="shrink-0" tabIndex={-1} aria-hidden>
        <BookCover
          bookId={loan.bookId}
          version={loan.coverVersion}
          alt=""
          sizes="64px"
          className="w-16"
        />
      </Link>
      <div className="grid min-w-0 flex-1 content-start gap-2">
        <div>
          <h2 className="font-semibold">
            <Link href={`/books/${loan.bookId}`} className="hover:underline">
              {loan.title}
            </Link>
          </h2>
          <p className="text-sm text-muted-foreground">
            {lost ? t("lostNote") : t("dueOn", { date: dueDate })}
            {loan.renewCount > 0 && <> · {t("renewedTimes", { count: loan.renewCount })}</>}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {lost ? (
            <Badge variant="destructive">{t("lost")}</Badge>
          ) : loan.overdue ? (
            <Badge variant="overdue">{t("overdueBy", { count: -days })}</Badge>
          ) : days <= DUE_SOON_DAYS ? (
            <Badge variant="on-loan">{t("dueIn", { count: days })}</Badge>
          ) : (
            <Badge variant="available">{t("dueIn", { count: days })}</Badge>
          )}
          {!lost && (
            <Button
              variant="outline"
              size="sm"
              disabled={renew.isPending}
              onClick={() => renew.mutate()}
            >
              <RefreshCw aria-hidden className={renew.isPending ? "animate-spin" : undefined} />
              {renew.isPending ? t("renewing") : t("renew")}
              <span className="sr-only">: {loan.title}</span>
            </Button>
          )}
        </div>

        {refused && (
          <div
            role="alert"
            className="grid gap-1 rounded-lg border border-on-loan/40 bg-on-loan/15 px-3 py-2 text-sm"
          >
            <p className="flex items-center gap-1.5 font-medium">
              <CircleAlert aria-hidden className="size-4 text-on-loan-foreground" />
              {t("cantRenew")}
            </p>
            <ul className="list-disc pl-6">
              {refused.problems.map((problem) => (
                <li key={problem.reason}>{t(`problems.${problem.reason}`)}</li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </li>
  );
}
