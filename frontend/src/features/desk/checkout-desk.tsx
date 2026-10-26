"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { MemberCard } from "@/features/desk/member-card";
import { ScanInput } from "@/features/desk/scan-input";
import { ApiError, api, type Schema } from "@/lib/api";

type CheckoutPreview = Schema<"CheckoutPreviewResponse">;

/** Library cards are "BH-000123"; anything else scanned at the desk is a book. */
export const isMemberCard = (code: string) => /^BH-\d+$/.test(code);

/**
 * Check out at the desk. Step 237: scanning a library card loads the member card with every
 * alert. A new card scanned at any time switches to that member.
 */
export function CheckoutDesk() {
  const t = useTranslations("Desk");
  const errorMessage = useAuthErrorMessage();
  const [memberNo, setMemberNo] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const preview = useQuery({
    queryKey: ["desk", "preview", memberNo],
    queryFn: () =>
      api.post<CheckoutPreview>("/staff/checkouts/preview", { memberNo, barcodes: [] }),
    enabled: memberNo !== null,
    // Always fresh: fines and loans change at other desks
    staleTime: 0,
    gcTime: 0,
    retry: false,
  });

  const onScan = (code: string) => {
    if (isMemberCard(code)) {
      setNotice(null);
      setMemberNo(code);
    } else if (!memberNo) {
      setNotice(t("scanCardFirst"));
    } else {
      // Book scanning arrives with the checkout list (step 238)
      setNotice(t("scanned", { code }));
    }
  };

  const notFound = preview.error instanceof ApiError && preview.error.code === "MEMBER_NOT_FOUND";
  const member = preview.data?.member;

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
        <div className="max-w-2xl">
          <MemberCard
            key={member.memberId}
            member={member}
            onClear={() => {
              setMemberNo(null);
              setNotice(null);
              document.getElementById("desk-scan")?.focus();
            }}
          />
        </div>
      )}
    </div>
  );
}
