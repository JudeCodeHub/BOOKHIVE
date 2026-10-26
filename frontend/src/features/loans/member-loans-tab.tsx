"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PackageX, RefreshCw } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { useStaffRenew } from "@/features/loans/use-staff-renew";
import { api, type Schema } from "@/lib/api";

type LoanSummary = Schema<"LoanSummary">;
type LoanCharge = Schema<"LoanChargeResponse">;

/**
 * The Loans tab of a member's profile (checklist step 247): what they have out now, with renew
 * (and admin override) and "mark lost" (charges replacement + fee, step 229). Only current loans:
 * a member's past reading is private, even from staff (PRD §4.5).
 */
export function MemberLoansTab({ memberId }: { memberId: string }) {
  const t = useTranslations("MemberLoansTab");
  const tLoans = useTranslations("StaffLoans");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const renewer = useStaffRenew();
  const [losing, setLosing] = useState<LoanSummary | null>(null);

  const loans = useQuery({
    // Under ["staff", "loans"]: renewals and returns anywhere refresh it
    queryKey: ["staff", "loans", "member", memberId],
    queryFn: () => api.get<LoanSummary[]>(`/staff/members/${memberId}/loans`),
  });

  const markLost = useMutation({
    mutationFn: (loan: LoanSummary) => api.post<LoanCharge>(`/staff/loans/${loan.loanId}/lost`),
    onSuccess: (charge) => {
      setLosing(null);
      toast.success(t("markedLost", { title: charge.title, total: money(charge.total) }));
      void queryClient.invalidateQueries({ queryKey: ["staff", "loans"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const money = (amount: number) =>
    format.number(amount, { style: "currency", currency: "LKR", currencyDisplay: "code" });
  const date = (iso: string) => format.dateTime(new Date(iso), { dateStyle: "medium" });

  if (loans.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (loans.isError) return <FormAlert>{errorMessage(loans.error)}</FormAlert>;
  if (loans.data.length === 0) {
    return (
      <p className="rounded-lg border p-6 text-center text-sm text-muted-foreground">
        {t("empty")}
      </p>
    );
  }

  return (
    <div className="grid gap-3">
      <ul className="grid gap-2">
        {loans.data.map((loan) => {
          const lost = loan.status === "LOST";
          return (
            <li
              key={loan.loanId}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
            >
              <div className="grid min-w-0 gap-0.5">
                <p className="font-medium">{loan.title}</p>
                <p className="text-sm text-muted-foreground">
                  <span className="font-mono">{loan.barcode}</span>
                  {" · "}
                  {t("outSince", { date: date(loan.checkedOutAt) })}
                  {" · "}
                  {t("due", { date: date(loan.dueDate) })}
                  {loan.renewCount > 0 && <> · {t("renewals", { count: loan.renewCount })}</>}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {lost ? (
                  <Badge variant="destructive">{t("lost")}</Badge>
                ) : loan.overdue ? (
                  <Badge variant="overdue">{tLoans("overdue")}</Badge>
                ) : (
                  <Badge variant="on-loan">{tLoans("onLoan")}</Badge>
                )}
                {!lost && (
                  <>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={renewer.isRenewing(loan.loanId)}
                      onClick={() => renewer.renew({ loanId: loan.loanId, title: loan.title })}
                    >
                      <RefreshCw aria-hidden />
                      {tLoans("renew")}
                      <span className="sr-only">: {loan.title}</span>
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setLosing(loan)}>
                      <PackageX aria-hidden />
                      {t("markLost")}
                      <span className="sr-only">: {loan.title}</span>
                    </Button>
                  </>
                )}
              </div>
            </li>
          );
        })}
      </ul>
      <p className="text-xs text-muted-foreground">{t("privacy")}</p>

      {renewer.dialog}

      <Dialog open={losing !== null} onOpenChange={(open) => !open && setLosing(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("lostTitle", { title: losing?.title ?? "" })}</DialogTitle>
            <DialogDescription>{t("lostText")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setLosing(null)}>
              {t("cancel")}
            </Button>
            <Button
              variant="destructive"
              disabled={markLost.isPending}
              onClick={() => losing && markLost.mutate(losing)}
            >
              {t("lostConfirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
