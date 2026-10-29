"use client";

import { BackLink } from "@/components/layout/back-link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ExternalLink, FileText } from "lucide-react";
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
import { Textarea } from "@/components/ui/textarea";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { APPLICATIONS_QUERY_KEY, yearsSince } from "@/features/staff/applications-queue";
import { MemberStatusBadge } from "@/features/staff/member-status-badge";
import { useRouter } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";

type Application = Schema<"ApplicationDetailResponse">;

/**
 * One application: the details, the uploaded document, and approve / reject (step 143).
 * Document links are presigned and expire after 5 minutes; reloading fetches new ones.
 */
export function ApplicationReview() {
  const t = useTranslations("StaffApplications.detail");
  const tCommon = useTranslations("Common");
  const v = useTranslations("Auth.validation");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { id } = useParams<{ id: string }>();
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: [...APPLICATIONS_QUERY_KEY, "detail", id],
    queryFn: () => api.get<Application>(`/staff/applications/${id}`),
  });

  const onDecided = async (message: string) => {
    toast.success(message);
    await queryClient.invalidateQueries({ queryKey: APPLICATIONS_QUERY_KEY });
    router.push("/staff/applications");
  };
  const onFailed = (error: Error) => {
    toast.error(
      error instanceof ApiError && error.code === "APPLICATION_NOT_PENDING"
        ? t("decided")
        : errorMessage(error),
    );
  };

  const approve = useMutation({
    mutationFn: () => api.post<Application>(`/staff/applications/${id}/approve`),
    onSuccess: (decided) => onDecided(t("approved", { memberNo: decided.memberNo ?? "" })),
    onError: onFailed,
  });
  const reject = useMutation({
    mutationFn: (text: string) =>
      api.post<Application>(`/staff/applications/${id}/reject`, { reason: text }),
    onSuccess: () => onDecided(t("rejected")),
    onError: onFailed,
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

  const application = query.data;
  const busy = approve.isPending || reject.isPending;
  const date = (iso: string) => format.dateTime(new Date(iso), { dateStyle: "medium" });

  function submitReject() {
    const text = reason.trim();
    if (!text) {
      setReasonError(v("required"));
      return;
    }
    reject.mutate(text);
  }

  return (
    <div className="grid gap-6">
      <BackLink href="/staff/applications">{t("back")}</BackLink>

      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-xl font-semibold">{application.fullName}</h1>
        <MemberStatusBadge status={application.status} />
        <span className="text-sm text-muted-foreground">{application.membershipType}</span>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <section aria-labelledby="applicant" className="rounded-lg border p-4">
          <h2 id="applicant" className="mb-3 font-medium">
            {t("applicant")}
          </h2>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
            <dt className="text-muted-foreground">{t("email")}</dt>
            <dd className="break-all">{application.email}</dd>
            <dt className="text-muted-foreground">{t("phone")}</dt>
            <dd>{application.phone ?? "–"}</dd>
            <dt className="text-muted-foreground">{t("dateOfBirth")}</dt>
            <dd>
              {date(application.dateOfBirth)} ·{" "}
              {t("ageNow", { age: yearsSince(application.dateOfBirth) })}
            </dd>
            <dt className="text-muted-foreground">{t("nic")}</dt>
            <dd className="font-mono">{application.nic ?? "–"}</dd>
            <dt className="text-muted-foreground">{t("address")}</dt>
            <dd>
              {[application.addressLine, application.city, application.postalCode]
                .filter(Boolean)
                .join(", ")}
            </dd>
            {application.guardianMemberNo && (
              <>
                <dt className="text-muted-foreground">{t("guardian")}</dt>
                <dd className="font-mono">{application.guardianMemberNo}</dd>
              </>
            )}
            <dt className="text-muted-foreground">{t("applied")}</dt>
            <dd>{date(application.appliedAt)}</dd>
          </dl>
        </section>

        <section aria-labelledby="documents" className="rounded-lg border p-4">
          <h2 id="documents" className="mb-3 font-medium">
            {t("documents")}
          </h2>
          {application.documents.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("noDocuments")}</p>
          ) : (
            <div className="grid gap-3">
              {application.documents.map((document) => (
                <figure key={document.id} className="grid gap-2">
                  {document.contentType.startsWith("image/") ? (
                    // A presigned link to private storage; next/image can't proxy it
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={document.url}
                      alt={`${document.kind} – ${application.fullName}`}
                      className="max-h-80 w-full rounded-md border object-contain"
                    />
                  ) : (
                    <FileText aria-hidden className="size-12 text-primary" />
                  )}
                  <figcaption className="text-sm">
                    <a
                      href={document.url}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1 underline-offset-4 hover:underline"
                    >
                      {document.kind} · {t("openDocument")}
                      <ExternalLink aria-hidden className="size-3" />
                    </a>
                  </figcaption>
                </figure>
              ))}
              <p className="text-xs text-muted-foreground">{t("linkNote")}</p>
            </div>
          )}
        </section>
      </div>

      {application.status === "PENDING" && (
        <div className="flex flex-wrap gap-3">
          <Button onClick={() => approve.mutate()} disabled={busy}>
            {approve.isPending ? t("approving") : t("approve")}
          </Button>
          <Button variant="destructive" onClick={() => setRejectOpen(true)} disabled={busy}>
            {t("reject")}
          </Button>
        </div>
      )}

      <Dialog open={rejectOpen} onOpenChange={setRejectOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("rejectTitle")}</DialogTitle>
            <DialogDescription>{t("rejectHelp")}</DialogDescription>
          </DialogHeader>
          <FormField label={t("reason")} error={reasonError ?? undefined} required>
            {(field) => (
              <Textarea
                {...field}
                rows={4}
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
            <Button variant="secondary" onClick={() => setRejectOpen(false)}>
              {t("cancel")}
            </Button>
            <Button variant="destructive" onClick={submitReject} disabled={reject.isPending}>
              {reject.isPending ? t("rejecting") : t("rejectConfirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
