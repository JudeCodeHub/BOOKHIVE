"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Mail, MessageSquare, RotateCw } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Segmented, SegmentedItem } from "@/components/ui/segmented";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Pager } from "@/features/staff/pager";
import { api, type PageResponse, type Schema } from "@/lib/api";

type Message = Schema<"OutboxMessageSummary">;
type Counts = Schema<"OutboxCountsResponse">;
type Status = Message["status"];

/**
 * Message delivery for admins (checklist step 319): how many emails/SMS are waiting, sent or
 * failed; the failed ones with the reason (e.g. the mail server refused the login) and a button to
 * send them again once that's fixed.
 */
export function OutboxMessages() {
  const t = useTranslations("AdminMessages");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Status>("FAILED");
  const [page, setPage] = useState(0);

  const counts = useQuery({
    queryKey: ["admin", "outbox", "counts"],
    queryFn: () => api.get<Counts>("/admin/notifications/messages/counts"),
    refetchInterval: 30_000,
  });
  const messages = useQuery({
    queryKey: ["admin", "outbox", status, page],
    queryFn: () =>
      api.get<PageResponse<Message>>("/admin/notifications/messages", {
        query: { status, page, size: 25 },
      }),
    placeholderData: keepPreviousData,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["admin", "outbox"] });

  const retry = useMutation({
    mutationFn: (id: string) => api.post(`/admin/notifications/messages/${id}/retry`),
    onSuccess: () => {
      toast.success(t("requeuedOne"));
      void refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const retryAll = useMutation({
    mutationFn: () => api.post<{ requeued: number }>("/admin/notifications/messages/retry-failed"),
    onSuccess: ({ requeued }) => {
      toast.success(t("requeuedAll", { count: requeued }));
      void refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const when = (iso: string | null | undefined) =>
    iso ? format.dateTime(new Date(iso), { dateStyle: "medium", timeStyle: "short" }) : "–";
  const data = messages.data;

  return (
    <div className="grid gap-4">
      {counts.data && (
        <dl className="grid grid-cols-3 gap-3 sm:max-w-lg">
          {(["pending", "sent", "failed"] as const).map((key) => (
            <div key={key} className="rounded-lg border p-3">
              <dt className="text-xs text-muted-foreground">{t(`counts.${key}`)}</dt>
              <dd
                className={
                  key === "failed" && counts.data.failed > 0
                    ? "text-2xl font-semibold text-destructive"
                    : "text-2xl font-semibold"
                }
              >
                {format.number(counts.data[key])}
              </dd>
            </div>
          ))}
        </dl>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <Segmented
          value={status}
          label={t("statuses")}
          onValueChange={(next) => {
            setStatus(next as Status);
            setPage(0);
          }}
        >
          <SegmentedItem value="FAILED">{t("status.FAILED")}</SegmentedItem>
          <SegmentedItem value="PENDING">{t("status.PENDING")}</SegmentedItem>
          <SegmentedItem value="SENT">{t("status.SENT")}</SegmentedItem>
        </Segmented>
        {status === "FAILED" && (counts.data?.failed ?? 0) > 0 && (
          <Button
            variant="outline"
            size="sm"
            disabled={retryAll.isPending}
            onClick={() => retryAll.mutate()}
          >
            <RotateCw aria-hidden />
            {t("retryAll")}
          </Button>
        )}
      </div>

      {messages.isPending ? (
        <div className="flex justify-center py-8">
          <HexLoader label={tCommon("loading")} />
        </div>
      ) : messages.isError ? (
        <FormAlert>{errorMessage(messages.error)}</FormAlert>
      ) : data && data.totalElements === 0 ? (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">
          {t(`empty.${status}`)}
        </p>
      ) : (
        data && (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("message")}</TableHead>
                  <TableHead>{t("recipient")}</TableHead>
                  <TableHead>{t("tries")}</TableHead>
                  <TableHead>{status === "SENT" ? t("sentAt") : t("updatedAt")}</TableHead>
                  {status === "FAILED" && (
                    <TableHead>
                      <span className="sr-only">{t("actions")}</span>
                    </TableHead>
                  )}
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((m) => (
                  <TableRow key={m.id} className="align-top">
                    <TableCell className="max-w-md">
                      <div className="flex items-center gap-1.5 font-medium">
                        {m.channel === "EMAIL" ? (
                          <Mail aria-label={t("email")} className="size-4 shrink-0" />
                        ) : (
                          <MessageSquare aria-label={t("sms")} className="size-4 shrink-0" />
                        )}
                        <Badge variant="outline">{m.type}</Badge>
                      </div>
                      {m.subject && <div className="mt-1 text-sm">{m.subject}</div>}
                      {m.lastError && (
                        <div className="mt-1 font-mono text-xs break-words text-destructive">
                          {m.lastError}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="text-sm">{m.recipient}</TableCell>
                    <TableCell>{m.attempts}</TableCell>
                    <TableCell className="text-sm whitespace-nowrap">
                      {when(status === "SENT" ? m.sentAt : m.updatedAt)}
                      {status === "PENDING" && m.attempts > 0 && (
                        <div className="text-xs text-muted-foreground">
                          {t("nextTry", { when: when(m.nextAttemptAt) })}
                        </div>
                      )}
                    </TableCell>
                    {status === "FAILED" && (
                      <TableCell className="text-right">
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={retry.isPending && retry.variables === m.id}
                          onClick={() => retry.mutate(m.id)}
                        >
                          <RotateCw aria-hidden />
                          {t("retry")}
                        </Button>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pager page={page} totalPages={data.totalPages} onChange={setPage} />
          </>
        )
      )}
      <p className="text-xs text-muted-foreground">{t("help")}</p>
    </div>
  );
}
