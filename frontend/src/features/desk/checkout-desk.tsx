"use client";

import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { LogOut } from "lucide-react";
import { useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { CheckoutList } from "@/features/desk/checkout-list";
import { CheckoutReceiptView } from "@/features/desk/checkout-receipt";
import { MemberCard } from "@/features/desk/member-card";
import { ScanInput } from "@/features/desk/scan-input";
import { ApiError, api, type Schema } from "@/lib/api";

type CheckoutPreview = Schema<"CheckoutPreviewResponse">;
type CheckoutReceipt = Schema<"CheckoutReceiptResponse">;

/** Library cards are "BH-000123"; anything else scanned at the desk is a book. */
export const isMemberCard = (code: string) => /^BH-\d+$/.test(code);

/** Most books in one checkout (the server's limit too). */
const MAX_ITEMS = 20;

/**
 * Check out at the desk. Scanning a library card loads the member card with every alert (step
 * 237); a new card at any time switches to that member and starts a new list. Each book scanned
 * after that is checked by the server right away: due date, or what's wrong (step 238). Confirm
 * lends them all (or none) and shows the receipt (step 239).
 */
export function CheckoutDesk() {
  const t = useTranslations("Desk");
  const errorMessage = useAuthErrorMessage();
  const [memberNo, setMemberNo] = useState<string | null>(null);
  const [barcodes, setBarcodes] = useState<string[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [emailReceipt, setEmailReceipt] = useState(true);
  const [receipt, setReceipt] = useState<{ data: CheckoutReceipt; emailed: boolean } | null>(null);

  const preview = useQuery({
    queryKey: ["desk", "preview", memberNo, barcodes],
    queryFn: () => api.post<CheckoutPreview>("/staff/checkouts/preview", { memberNo, barcodes }),
    enabled: memberNo !== null,
    // Keep showing the list while a new scan is checked, but never another member's data
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[2] === memberNo ? previous : undefined,
    // Always fresh: fines and loans change at other desks
    staleTime: 0,
    gcTime: 0,
    retry: false,
  });

  const checkout = useMutation({
    mutationFn: () =>
      api.post<CheckoutReceipt>("/staff/checkouts", { memberNo, barcodes, emailReceipt }),
    onSuccess: (data) => {
      setReceipt({ data, emailed: emailReceipt });
      setBarcodes([]);
      setNotice(null);
    },
    onError: (error) => {
      // Something changed since the last check (e.g. another desk lent a copy): show it
      setNotice(
        error instanceof ApiError && error.code === "CHECKOUT_REFUSED"
          ? t("checkoutRefused")
          : errorMessage(error),
      );
      void preview.refetch();
    },
  });

  const onScan = (code: string) => {
    setReceipt(null);
    if (isMemberCard(code)) {
      setNotice(null);
      if (code !== memberNo) {
        setMemberNo(code);
        setBarcodes([]);
      }
    } else if (!memberNo || preview.isError) {
      setNotice(t("scanCardFirst"));
    } else if (barcodes.includes(code)) {
      setNotice(t("alreadyInList", { code }));
    } else if (barcodes.length >= MAX_ITEMS) {
      setNotice(t("tooManyItems", { max: MAX_ITEMS }));
    } else {
      setNotice(null);
      setBarcodes([...barcodes, code]);
    }
  };

  const clear = () => {
    setReceipt(null);
    setMemberNo(null);
    setBarcodes([]);
    setNotice(null);
    document.getElementById("desk-scan")?.focus();
  };

  const notFound = preview.error instanceof ApiError && preview.error.code === "MEMBER_NOT_FOUND";
  const member = preview.data?.member;
  const items = preview.data?.items ?? [];
  // Only a check of exactly this list counts, not the one shown while re-checking
  const canConfirm =
    preview.data?.canCheckOut === true && !preview.isPlaceholderData && !preview.isFetching;

  return (
    <div className="grid gap-6">
      <ScanInput
        id="desk-scan"
        label={member ? t("scanItemLabel") : t("scanMemberLabel")}
        hint={member ? t("scanItemHint") : t("scanMemberHint")}
        placeholder={t("placeholder")}
        onScan={onScan}
        busy={preview.isFetching}
        className="max-w-2xl"
      />

      <p role="status" aria-live="polite" className="min-h-5 text-sm text-muted-foreground">
        {notice}
      </p>

      {preview.isPending && memberNo && (
        <div className="flex justify-center py-8">
          <HexLoader label={t("loadingMember")} />
        </div>
      )}
      {preview.isError && (
        <div className="max-w-2xl">
          <FormAlert>
            {notFound
              ? t("memberNotFound", { memberNo: memberNo ?? "" })
              : errorMessage(preview.error)}
          </FormAlert>
        </div>
      )}
      {receipt && (
        <CheckoutReceiptView
          receipt={receipt.data}
          emailed={receipt.emailed}
          onNextMember={clear}
        />
      )}
      {member && !receipt && (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
          <MemberCard key={member.memberId} member={member} onClear={clear} />
          <div className="grid gap-4">
            <CheckoutList
              items={items}
              memberBlocked={member.problems.length > 0}
              onRemove={(barcode) => {
                setBarcodes(barcodes.filter((code) => code !== barcode));
                document.getElementById("desk-scan")?.focus();
              }}
            />
            {items.length > 0 && (
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-card p-3">
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4 accent-primary"
                    checked={emailReceipt}
                    onChange={(event) => setEmailReceipt(event.target.checked)}
                  />
                  {t("emailReceipt")}
                </label>
                <Button
                  size="lg"
                  disabled={!canConfirm || checkout.isPending}
                  onClick={() => checkout.mutate()}
                >
                  <LogOut aria-hidden />
                  {checkout.isPending
                    ? t("checkingOut")
                    : t("confirmCheckout", { count: items.length })}
                </Button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
