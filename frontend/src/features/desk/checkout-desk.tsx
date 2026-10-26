"use client";

import { useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { LogOut, ShieldAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { useSession } from "@/features/auth/use-session";
import { CheckoutList } from "@/features/desk/checkout-list";
import { CheckoutReceiptView } from "@/features/desk/checkout-receipt";
import { Kbd } from "@/features/desk/kbd";
import { MemberCard } from "@/features/desk/member-card";
import { OverrideDialog } from "@/features/desk/override-dialog";
import { ScanInput } from "@/features/desk/scan-input";
import { playSound, soundForProblems } from "@/features/desk/sounds";
import { useShortcuts } from "@/features/desk/use-shortcuts";
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
 *
 * When every problem may be bent, an admin can lend anyway with a written reason (step 246).
 *
 * Every scan sounds its result (step 241). Shortcuts: F8 check out, F9 print the receipt, Esc next
 * member.
 */
export function CheckoutDesk() {
  const t = useTranslations("Desk");
  const errorMessage = useAuthErrorMessage();
  const [memberNo, setMemberNo] = useState<string | null>(null);
  const [barcodes, setBarcodes] = useState<string[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [emailReceipt, setEmailReceipt] = useState(true);
  const [overriding, setOverriding] = useState(false);
  const isAdmin = useSession().hasRole("ADMIN");
  const [receipt, setReceipt] = useState<{ data: CheckoutReceipt; emailed: boolean } | null>(null);
  // The code just scanned: the next check's result is sounded for it (not for removals)
  const soundFor = useRef<string | null>(null);

  const preview = useQuery({
    queryKey: ["desk", "preview", memberNo, barcodes],
    queryFn: async () => {
      const code = soundFor.current;
      soundFor.current = null;
      try {
        const data = await api.post<CheckoutPreview>("/staff/checkouts/preview", {
          memberNo,
          barcodes,
        });
        if (code && isMemberCard(code)) {
          playSound(soundForProblems(data.member.problems));
        } else if (code) {
          const item = data.items.find((i) => i.barcode === code);
          playSound(item?.copyId == null ? "error" : soundForProblems(item.problems));
        }
        return data;
      } catch (error) {
        if (code) playSound("error");
        throw error;
      }
    },
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
    mutationFn: (overrideReason?: string) =>
      api.post<CheckoutReceipt>("/staff/checkouts", {
        memberNo,
        barcodes,
        emailReceipt,
        overrideReason,
      }),
    onSuccess: (data) => {
      playSound("success");
      setOverriding(false);
      setReceipt({ data, emailed: emailReceipt });
      setBarcodes([]);
      setNotice(null);
    },
    onError: (error) => {
      playSound("error");
      setOverriding(false);
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
        soundFor.current = code;
        setMemberNo(code);
        setBarcodes([]);
      }
    } else if (!memberNo || preview.isError) {
      playSound("warning");
      setNotice(t("scanCardFirst"));
    } else if (barcodes.includes(code)) {
      playSound("warning");
      setNotice(t("alreadyInList", { code }));
    } else if (barcodes.length >= MAX_ITEMS) {
      playSound("warning");
      setNotice(t("tooManyItems", { max: MAX_ITEMS }));
    } else {
      soundFor.current = code;
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
  const canOverride =
    isAdmin &&
    preview.data?.canOverride === true &&
    !preview.isPlaceholderData &&
    !preview.isFetching;
  // The rules an override sets aside, once each, in the desk's language
  const overriddenRules = [
    ...new Set(
      [...(member?.problems ?? []), ...items.flatMap((item) => item.problems)].map((p) =>
        t(`problems.${p.reason}`),
      ),
    ),
  ];

  useShortcuts({
    F8: () => {
      if (overriding || !canConfirm || checkout.isPending || receipt) return false;
      checkout.mutate();
    },
    F9: () => {
      if (!receipt) return false;
      window.print();
    },
    Escape: () => {
      // While the override dialog is open, Esc only closes the dialog
      if (overriding || (!memberNo && !receipt)) return false;
      clear();
    },
  });

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
      <OverrideDialog
        open={overriding}
        onOpenChange={setOverriding}
        title={t("overrideTitle", { count: items.length })}
        rules={overriddenRules}
        confirmLabel={t("overrideConfirm", { count: items.length })}
        pending={checkout.isPending}
        onConfirm={(reason) => checkout.mutate(reason)}
      />

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
                {canOverride && (
                  <Button variant="outline" size="lg" onClick={() => setOverriding(true)}>
                    <ShieldAlert aria-hidden />
                    {t("overrideButton")}
                  </Button>
                )}
                <Button
                  size="lg"
                  disabled={!canConfirm || checkout.isPending}
                  onClick={() => checkout.mutate(undefined)}
                >
                  <LogOut aria-hidden />
                  {checkout.isPending
                    ? t("checkingOut")
                    : t("confirmCheckout", { count: items.length })}
                  <Kbd>F8</Kbd>
                </Button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
