import { Suspense } from "react";
import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { AuditLog } from "@/features/admin/audit-log";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("AdminAudit");
  return { title: t("title") };
}

export default function AuditLogPage() {
  const t = useTranslations("AdminAudit");

  return (
    <div className="grid gap-4">
      <div>
        <h1 className="text-xl font-semibold">{t("title")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{t("description")}</p>
      </div>
      {/* The filters are read from the address, only known in the browser */}
      <Suspense>
        <AuditLog />
      </Suspense>
    </div>
  );
}
