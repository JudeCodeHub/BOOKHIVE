"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { useSession } from "@/features/auth/use-session";
import { OverrideDialog } from "@/features/desk/override-dialog";
import { type RenewalCheck, refusedRenewal } from "@/features/loans/renewal-reasons";
import { api, type Schema } from "@/lib/api";

type Renewal = Schema<"LoanRenewalResponse">;

/** What staff need to know about a loan to renew it. */
export type RenewableLoan = { loanId: string; title: string };

/**
 * Renewing a loan from a staff screen (steps 243, 246, 247): toast with the new due date, or the
 * reasons it was refused; for admins, an Override action that opens the reason dialog. Render
 * `dialog` once in the screen. Every staff loan list refreshes afterwards (["staff", "loans"]).
 */
export function useStaffRenew() {
  const t = useTranslations("StaffLoans");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const isAdmin = useSession().hasRole("ADMIN");
  const [override, setOverride] = useState<{ loan: RenewableLoan; check: RenewalCheck } | null>(
    null,
  );

  const mutation = useMutation({
    mutationFn: ({ loan, overrideReason }: { loan: RenewableLoan; overrideReason?: string }) =>
      api.post<Renewal>(`/staff/loans/${loan.loanId}/renew`, { overrideReason }),
    onSuccess: (renewal) => {
      setOverride(null);
      toast.success(
        t("renewed", {
          title: renewal.title,
          date: format.dateTime(new Date(renewal.dueDate), { dateStyle: "medium" }),
        }),
      );
      void queryClient.invalidateQueries({ queryKey: ["staff", "loans"] });
    },
    onError: (error, { loan, overrideReason }) => {
      const refused = refusedRenewal(error);
      if (!refused) {
        toast.error(errorMessage(error));
        return;
      }
      setOverride(null);
      toast.error(t("renewRefused"), {
        description: refused.problems.map((p) => t(`renewalProblems.${p.reason}`)).join(" "),
        // Admins can bend these rules with a reason (not again if an override just failed)
        action:
          isAdmin && refused.canOverride && !overrideReason
            ? { label: t("override"), onClick: () => setOverride({ loan, check: refused }) }
            : undefined,
      });
    },
  });

  const dialog = (
    <OverrideDialog
      open={override !== null}
      onOpenChange={(open) => !open && setOverride(null)}
      title={t("overrideTitle", { title: override?.loan.title ?? "" })}
      rules={(override?.check.problems ?? []).map((p) => t(`renewalProblems.${p.reason}`))}
      confirmLabel={t("overrideConfirm")}
      pending={mutation.isPending}
      onConfirm={(reason) =>
        override && mutation.mutate({ loan: override.loan, overrideReason: reason })
      }
    />
  );

  return {
    renew: (loan: RenewableLoan) => mutation.mutate({ loan }),
    /** This loan's renewal is on its way (disable its button) */
    isRenewing: (loanId: string) =>
      mutation.isPending && mutation.variables?.loan.loanId === loanId,
    dialog,
  };
}
