"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import type { Schema } from "@/lib/api";

type Status = Schema<"MemberSummary">["status"];

// Same status colours as the rest of the site (PRD §11): green good, amber attention, red blocked
const VARIANT = {
  ACTIVE: "available",
  PENDING: "outline",
  EXPIRED: "on-loan",
  SUSPENDED: "overdue",
  REJECTED: "destructive",
} as const;

export function MemberStatusBadge({ status }: { status: Status }) {
  const t = useTranslations("MemberStatusLabel");
  return <Badge variant={VARIANT[status]}>{t(status)}</Badge>;
}
