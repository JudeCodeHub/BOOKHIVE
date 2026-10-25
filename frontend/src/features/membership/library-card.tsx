"use client";

import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { LogoMark } from "@/components/brand/logo";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";

type Card = Schema<"CardResponse">;

/**
 * The digital library card (checklist step 141): shown at the desk, scanned from the screen.
 * Barcode and QR are PNGs made by the backend (ZXing); they need the access token, so they're
 * fetched as Blobs and shown through blob: URLs.
 */
export function LibraryCard() {
  const t = useTranslations("LibraryCard");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const [downloading, setDownloading] = useState(false);

  const card = useQuery({
    queryKey: ["me", "card"],
    queryFn: () => api.get<Card>("/me/card"),
    // "Not a member yet" is an answer, not a hiccup worth retrying
    retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
  });
  const barcode = useImage("/me/card/barcode.png", card.isSuccess);
  const qr = useImage("/me/card/qr.png", card.isSuccess);

  if (card.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (card.isError) {
    const notMember =
      card.error instanceof ApiError &&
      ["CARD_NOT_AVAILABLE", "MEMBERSHIP_NOT_FOUND"].includes(card.error.code);
    return notMember ? (
      <div className="grid justify-items-start gap-3 rounded-lg border p-4">
        <p>{t("notAvailable")}</p>
        <Button asChild variant="secondary">
          <Link href="/account/membership">{t("goToMembership")}</Link>
        </Button>
      </div>
    ) : (
      <FormAlert>{errorMessage(card.error)}</FormAlert>
    );
  }

  const { memberNo, fullName, membershipType, expiresAt } = card.data;

  async function downloadPdf() {
    setDownloading(true);
    try {
      const pdf = await api.getBlob("/me/card.pdf");
      const url = URL.createObjectURL(pdf);
      const link = document.createElement("a");
      link.href = url;
      link.download = `bookhive-card-${memberNo}.pdf`;
      link.click();
      URL.revokeObjectURL(url);
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="grid max-w-md gap-4">
      {/* Bank-card proportions (85.6 x 54 mm), like the PDF */}
      <section
        aria-label={t("label")}
        className="grid gap-3 rounded-2xl border bg-card p-5 shadow-sm"
      >
        <header className="flex items-center gap-2">
          <LogoMark className="size-7" />
          <span className="font-heading text-lg font-semibold">BookHive</span>
          <span className="ml-auto rounded bg-primary px-2 py-0.5 text-xs font-medium text-primary-foreground">
            {t("label")}
          </span>
        </header>

        <div className="flex items-start gap-4">
          <dl className="grid min-w-0 flex-1 gap-1 text-sm">
            <dt className="sr-only">{t("member")}</dt>
            <dd className="truncate text-base font-semibold">{fullName}</dd>
            <dt className="sr-only">{t("memberNo")}</dt>
            <dd className="font-mono text-xl font-bold tracking-wide">{memberNo}</dd>
            <dt className="sr-only">{t("type")}</dt>
            <dd>{membershipType}</dd>
            <dt className="text-muted-foreground">{t("validUntil")}</dt>
            <dd className="font-medium">
              {format.dateTime(new Date(expiresAt), { dateStyle: "long" })}
            </dd>
          </dl>
          {qr && (
            // A blob: URL of a server-made PNG; next/image can't optimise those
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={qr}
              alt={t("qr", { memberNo })}
              className="size-24 shrink-0 rounded bg-white p-1"
            />
          )}
        </div>

        {barcode && (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={barcode}
            alt={t("barcode", { memberNo })}
            className="h-16 w-full rounded bg-white object-contain"
          />
        )}
      </section>

      <p className="text-sm text-muted-foreground">{t("help")}</p>
      <Button
        onClick={() => void downloadPdf()}
        disabled={downloading}
        className="justify-self-start"
      >
        <Download aria-hidden />
        {downloading ? t("downloading") : t("download")}
      </Button>
    </div>
  );
}

/** Loads a protected image and returns a blob: URL for <img>, freed when no longer shown. */
function useImage(path: string, enabled: boolean) {
  const query = useQuery({
    queryKey: ["me", "card-image", path],
    queryFn: () => api.getBlob(path),
    enabled,
    staleTime: Infinity,
  });
  const url = useMemo(() => (query.data ? URL.createObjectURL(query.data) : null), [query.data]);
  useEffect(
    () => () => {
      if (url) URL.revokeObjectURL(url);
    },
    [url],
  );
  return url;
}
