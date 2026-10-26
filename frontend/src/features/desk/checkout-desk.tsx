"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { CheckoutList } from "@/features/desk/checkout-list";
import { MemberCard } from "@/features/desk/member-card";
import { ScanInput } from "@/features/desk/scan-input";
import { ApiError, api, type Schema } from "@/lib/api";

type CheckoutPreview = Schema<"CheckoutPreviewResponse">;

/** Library cards are "BH-000123"; anything else scanned at the desk is a book. */
export const isMemberCard = (code: string) => /^BH-\d+$/.test(code);

/** Most books in one checkout (the server's limit too). */
const MAX_ITEMS = 20;

/**
 * Check out at the desk. Scanning a library card loads the member card with every alert (step
 * 237); a new card at any time switches to that member and starts a new list. Each book scanned
 * after that is checked by the server right away: due date, or what's wrong (step 238).
 */
export function CheckoutDesk() {
  const t = useTranslations("Desk");
  const errorMessage = useAuthErrorMessage();
  const [memberNo, setMemberNo] = useState<string | null>(null);
  const [barcodes, setBarcodes] = useState<string[]>([]);
  const [notice, setNotice] = useState<string | null>(null);

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

  const onScan = (code: string) => {
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
    setMemberNo(null);
    setBarcodes([]);
    setNotice(null);
    document.getElementById("desk-scan")?.focus();
  };

  const notFound = preview.error instanceof ApiError && preview.error.code === "MEMBER_NOT_FOUND";
  const member = preview.data?.member;
  const items = preview.data?.items ?? [];

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
      {member && (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
          <MemberCard key={member.memberId} member={member} onClear={clear} />
          <CheckoutList
            items={items}
            memberBlocked={member.problems.length > 0}
            onRemove={(barcode) => {
              setBarcodes(barcodes.filter((code) => code !== barcode));
              document.getElementById("desk-scan")?.focus();
            }}
          />
        </div>
      )}
    </div>
  );
}
