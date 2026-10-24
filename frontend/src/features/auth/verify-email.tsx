"use client";

import { useQuery } from "@tanstack/react-query";
import { CircleCheck, CircleX } from "lucide-react";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import { Link } from "@/i18n/navigation";
import { ApiError, api } from "@/lib/api";

/**
 * Landing page of the "verify your email" link: sends the token from the URL once and shows the
 * result.
 *
 * A query (not an effect) on purpose: React runs effects twice in development, and a second POST
 * with the same token would answer "already used" right after it succeeded. TanStack Query sends
 * one request per token and keeps the answer.
 */
export function VerifyEmail() {
  const t = useTranslations("Auth.verify");
  const token = useSearchParams().get("token");

  const result = useQuery({
    queryKey: ["verify-email", token],
    // The endpoint answers 204 (no body). TanStack Query treats `undefined` data as an error,
    // so return an explicit value once the call succeeded.
    queryFn: async () => {
      await api.post("/auth/verify-email", { token });
      return true;
    },
    enabled: !!token,
    retry: false,
    staleTime: Infinity,
    gcTime: Infinity,
  });

  if (!token) {
    return <Outcome ok={false} title={t("missingTitle")} text={t("missingText")} />;
  }
  if (result.isPending) {
    return (
      <div className="flex flex-col items-center gap-3 py-6 text-center text-muted-foreground">
        <HexLoader label={t("checking")} />
        <p>{t("checking")}</p>
      </div>
    );
  }
  if (result.isSuccess) {
    return (
      <Outcome ok title={t("successTitle")} text={t("successText")}>
        <Button asChild>
          <Link href="/login">{t("login")}</Link>
        </Button>
      </Outcome>
    );
  }

  const expired =
    result.error instanceof ApiError && result.error.code === "VERIFICATION_TOKEN_EXPIRED";
  return expired ? (
    <Outcome ok={false} title={t("expiredTitle")} text={t("expiredText")}>
      <Button asChild>
        <Link href="/forgot-password">{t("forgot")}</Link>
      </Button>
    </Outcome>
  ) : (
    <Outcome ok={false} title={t("invalidTitle")} text={t("invalidText")}>
      <Button asChild variant="secondary">
        <Link href="/login">{t("login")}</Link>
      </Button>
    </Outcome>
  );
}

function Outcome({
  ok,
  title,
  text,
  children,
}: {
  ok: boolean;
  title: string;
  text: string;
  children?: React.ReactNode;
}) {
  const Icon = ok ? CircleCheck : CircleX;
  return (
    <div role="status" className="flex flex-col items-center gap-3 text-center">
      <Icon aria-hidden className={ok ? "size-10 text-available" : "size-10 text-destructive"} />
      <h2 className="font-sans text-lg font-semibold">{title}</h2>
      <p className="text-muted-foreground">{text}</p>
      {children && <div className="mt-2">{children}</div>}
    </div>
  );
}
