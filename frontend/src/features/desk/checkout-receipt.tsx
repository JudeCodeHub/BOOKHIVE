"use client";

import { CircleCheck, Mail, Printer, UserRoundPlus } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { PrintArea } from "@/components/print/print-area";
import { Button } from "@/components/ui/button";
import type { Schema } from "@/lib/api";

type CheckoutReceipt = Schema<"CheckoutReceiptResponse">;

/**
 * After checkout (checklist step 239): what was lent and when it's due, a printable slip (sized
 * for an 80 mm receipt printer, fine on A4 too), and whether the receipt was emailed.
 */
export function CheckoutReceiptView({
  receipt,
  emailed,
  onNextMember,
}: {
  receipt: CheckoutReceipt;
  emailed: boolean;
  onNextMember: () => void;
}) {
  const t = useTranslations("Desk");
  const format = useFormatter();
  const { member, loans } = receipt;
  const due = (iso: string) =>
    format.dateTime(new Date(iso), {
      weekday: "short",
      day: "numeric",
      month: "short",
      year: "numeric",
    });
  const checkedOutAt = loans[0]?.checkedOutAt;

  return (
    <section
      aria-labelledby="desk-receipt-title"
      className="grid max-w-2xl gap-4 rounded-xl border border-available/40 bg-card p-4 sm:p-5"
    >
      <div className="flex items-start gap-3">
        <CircleCheck aria-hidden className="mt-0.5 size-6 shrink-0 text-available-foreground" />
        <div className="grid gap-1">
          <h2 id="desk-receipt-title" role="status" className="text-lg font-semibold">
            {t("receiptTitle", { count: loans.length, name: member.fullName })}
          </h2>
          {emailed && (
            <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
              <Mail aria-hidden className="size-4" />
              {t("receiptEmailed")}
            </p>
          )}
        </div>
      </div>

      <ol className="grid gap-2">
        {loans.map((loan) => (
          <li
            key={loan.loanId}
            className="flex flex-wrap items-baseline justify-between gap-x-4 rounded-lg bg-muted/50 px-3 py-2"
          >
            <span className="font-medium">{loan.title}</span>
            <span className="text-sm">{t("dueOn", { date: due(loan.dueDate) })}</span>
          </li>
        ))}
      </ol>

      <div className="flex flex-wrap gap-2">
        <Button onClick={onNextMember} autoFocus>
          <UserRoundPlus aria-hidden />
          {t("nextMember")}
        </Button>
        <Button variant="outline" onClick={() => window.print()}>
          <Printer aria-hidden />
          {t("printReceipt")}
        </Button>
      </div>

      <PrintArea>
        <div className="mx-auto max-w-[80mm] p-2 font-mono text-[11px] leading-snug">
          <p className="text-center text-sm font-bold">BookHive</p>
          <p className="text-center">{t("slipHeading")}</p>
          <hr className="my-2 border-dashed border-black" />
          <p>{member.fullName}</p>
          <p>{member.memberNo}</p>
          {checkedOutAt && (
            <p>
              {format.dateTime(new Date(checkedOutAt), { dateStyle: "medium", timeStyle: "short" })}
            </p>
          )}
          <hr className="my-2 border-dashed border-black" />
          <ol className="grid gap-1.5">
            {loans.map((loan) => (
              <li key={loan.loanId}>
                <p>{loan.title}</p>
                <p>
                  {loan.barcode} · {t("dueOn", { date: due(loan.dueDate) })}
                </p>
              </li>
            ))}
          </ol>
          <hr className="my-2 border-dashed border-black" />
          <p>{t("slipCount", { count: loans.length })}</p>
          <p className="mt-1">{t("slipFooter")}</p>
        </div>
      </PrintArea>
    </section>
  );
}
