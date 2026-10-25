"use client";

import { useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { useParams } from "next/navigation";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { yearsSince } from "@/features/staff/applications-queue";
import { MemberStatusBadge } from "@/features/staff/member-status-badge";
import { LANGUAGES } from "@/i18n/languages";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";

type Profile = Schema<"MemberProfileResponse">;
type Renewal = Schema<"RenewalResponse">;

/**
 * A member's page for the desk (checklist step 144): details now; loans, holds, fines and
 * history tabs fill in with the circulation phase. Renew, suspend and reactivate are here too.
 */
export function MemberProfile() {
  const t = useTranslations("StaffMembers.profile");
  const tCommon = useTranslations("Common");
  const tErrors = useTranslations("Membership.errors");
  const v = useTranslations("Auth.validation");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const { id } = useParams<{ id: string }>();
  const [suspendOpen, setSuspendOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState<string | null>(null);

  const queryKey = ["staff", "members", "profile", id];
  const query = useQuery({
    queryKey,
    queryFn: () => api.get<Profile>(`/staff/members/${id}`),
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["staff", "members"] });
  const onError = (error: Error) => {
    const code = error instanceof ApiError ? error.code : "";
    toast.error(tErrors.has(code as never) ? tErrors(code as never) : errorMessage(error));
  };
  const date = (iso: string) => format.dateTime(new Date(iso), { dateStyle: "medium" });

  const renew = useMutation({
    mutationFn: () => api.post<Renewal>(`/staff/members/${id}/renew`),
    onSuccess: async (renewal) => {
      toast.success(t("renewed", { date: date(renewal.expiresAt) }));
      await refresh();
    },
    onError,
  });
  const suspend = useMutation({
    mutationFn: (text: string) => api.post(`/staff/members/${id}/suspend`, { reason: text }),
    onSuccess: async () => {
      toast.success(t("suspended"));
      setSuspendOpen(false);
      setReason("");
      await refresh();
    },
    onError,
  });
  const reactivate = useMutation({
    mutationFn: () => api.post(`/staff/members/${id}/reactivate`, {}),
    onSuccess: async () => {
      toast.success(t("reactivated"));
      await refresh();
    },
    onError,
  });

  if (query.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (query.isError) {
    return <FormAlert>{errorMessage(query.error)}</FormAlert>;
  }

  const member = query.data;
  const busy = renew.isPending || suspend.isPending || reactivate.isPending;
  const language = LANGUAGES.find((item) => item.code === member.preferredLanguage)?.name;

  return (
    <div className="grid gap-6">
      <Link
        href="/staff/members"
        className="inline-flex items-center gap-1 justify-self-start text-sm text-muted-foreground underline-offset-4 hover:underline"
      >
        <ArrowLeft aria-hidden className="size-4" />
        {t("back")}
      </Link>

      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-xl font-semibold">{member.fullName}</h1>
        <span className="font-mono text-lg">{member.memberNo}</span>
        <MemberStatusBadge status={member.status} />
      </div>
      {member.statusReason && (
        <p className="text-sm">
          <span className="text-muted-foreground">{t("reason")}: </span>
          {member.statusReason}
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        {(member.status === "ACTIVE" || member.status === "EXPIRED") && (
          <Button size="sm" onClick={() => renew.mutate()} disabled={busy}>
            {t("renew")}
          </Button>
        )}
        {member.status === "ACTIVE" && (
          <Button
            size="sm"
            variant="destructive"
            onClick={() => setSuspendOpen(true)}
            disabled={busy}
          >
            {t("suspend")}
          </Button>
        )}
        {member.status === "SUSPENDED" && (
          <Button size="sm" onClick={() => reactivate.mutate()} disabled={busy}>
            {t("reactivate")}
          </Button>
        )}
      </div>

      <Tabs defaultValue="details">
        <TabsList aria-label={t("tabs")}>
          <TabsTrigger value="details">{t("details")}</TabsTrigger>
          <TabsTrigger value="loans">{t("loans")}</TabsTrigger>
          <TabsTrigger value="holds">{t("holds")}</TabsTrigger>
          <TabsTrigger value="fines">{t("fines")}</TabsTrigger>
          <TabsTrigger value="history">{t("history")}</TabsTrigger>
        </TabsList>

        <TabsContent value="details" className="grid gap-6 pt-2 lg:grid-cols-2">
          <dl className="grid grid-cols-[auto_1fr] content-start gap-x-4 gap-y-2 rounded-lg border p-4 text-sm">
            <Row label={t("email")}>{member.email}</Row>
            <Row label={t("phone")}>{member.phone ?? "–"}</Row>
            <Row label={t("language")}>{language ?? member.preferredLanguage}</Row>
            <Row label={t("type")}>
              {member.membershipType}
              <span className="block text-muted-foreground">
                {t("limits", { items: member.maxItems, days: member.loanDays })}
              </span>
            </Row>
            <Row label={t("dateOfBirth")}>
              {date(member.dateOfBirth)} ({yearsSince(member.dateOfBirth)})
            </Row>
            <Row label={t("nic")}>
              <span className="font-mono">{member.nic ?? "–"}</span>
            </Row>
            <Row label={t("address")}>
              {[member.addressLine, member.city, member.postalCode].filter(Boolean).join(", ")}
            </Row>
            {member.approvedAt && <Row label={t("approved")}>{date(member.approvedAt)}</Row>}
            {member.expiresAt && <Row label={t("expires")}>{date(member.expiresAt)}</Row>}
          </dl>

          {(member.guardian || member.juniors.length > 0) && (
            <div className="grid content-start gap-4 rounded-lg border p-4 text-sm">
              {member.guardian && (
                <div>
                  <h2 className="mb-1 font-medium">{t("guardian")}</h2>
                  <MemberLink member={member.guardian} />
                </div>
              )}
              {member.juniors.length > 0 && (
                <div>
                  <h2 className="mb-1 font-medium">{t("juniors")}</h2>
                  <ul className="grid gap-1">
                    {member.juniors.map((junior) => (
                      <li key={junior.id}>
                        <MemberLink member={junior} />
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </TabsContent>

        {(["loans", "holds", "fines", "history"] as const).map((tab) => (
          <TabsContent key={tab} value={tab} className="pt-2">
            <p className="rounded-lg border p-6 text-center text-sm text-muted-foreground">
              {t("comingSoon")}
            </p>
          </TabsContent>
        ))}
      </Tabs>

      <Dialog open={suspendOpen} onOpenChange={setSuspendOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("suspendTitle")}</DialogTitle>
            <DialogDescription>{t("suspendHelp")}</DialogDescription>
          </DialogHeader>
          <FormField label={t("reason")} error={reasonError ?? undefined} required>
            {(field) => (
              <Textarea
                {...field}
                rows={3}
                maxLength={500}
                value={reason}
                onChange={(event) => {
                  setReason(event.target.value);
                  setReasonError(null);
                }}
              />
            )}
          </FormField>
          <DialogFooter>
            <Button variant="secondary" onClick={() => setSuspendOpen(false)}>
              {t("cancel")}
            </Button>
            <Button
              variant="destructive"
              disabled={suspend.isPending}
              onClick={() =>
                reason.trim() ? suspend.mutate(reason.trim()) : setReasonError(v("required"))
              }
            >
              {suspend.isPending ? t("working") : t("suspendConfirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="break-words">{children}</dd>
    </>
  );
}

function MemberLink({ member }: { member: Schema<"MemberSummary"> }) {
  return (
    <Link href={`/staff/members/${member.id}`} className="underline-offset-4 hover:underline">
      <span className="font-mono">{member.memberNo}</span> · {member.fullName}
    </Link>
  );
}
