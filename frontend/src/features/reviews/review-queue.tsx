"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
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
import { Pager } from "@/features/staff/pager";
import { Link } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";
import { Stars } from "./stars";

type PendingReview = Schema<"PendingReviewSummary">;

const QUEUE_KEY = ["staff", "reviews", "pending"] as const;

/** Ready-made reasons staff can pick, then adjust; the member sees the reason. */
const QUICK_REASONS = ["spoilers", "offensive", "offTopic", "personal"] as const;

/**
 * Review moderation (COMM-1, checklist step 349): reviews waiting for staff, oldest first.
 * Approve puts it on the book page; reject needs a reason, which the member sees (they can
 * change the review and send it again).
 */
export function ReviewQueue() {
  const t = useTranslations("ReviewQueue");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [rejecting, setRejecting] = useState<PendingReview | null>(null);
  const [reason, setReason] = useState("");

  const query = useQuery({
    queryKey: [...QUEUE_KEY, page],
    queryFn: () =>
      api.get<PageResponse<PendingReview>>("/staff/reviews/pending", {
        query: { page, size: 20 },
      }),
    placeholderData: keepPreviousData,
  });

  const done = (review: PendingReview) => {
    void queryClient.invalidateQueries({ queryKey: QUEUE_KEY });
    void queryClient.invalidateQueries({ queryKey: ["reviews", review.bookId] });
  };
  const approve = useMutation({
    mutationFn: (review: PendingReview) => api.post(`/staff/reviews/${review.id}/approve`),
    onSuccess: (_, review) => {
      toast.success(t("approved", { title: review.bookTitle }));
      done(review);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const reject = useMutation({
    mutationFn: (review: PendingReview) =>
      api.post(`/staff/reviews/${review.id}/reject`, { reason: reason.trim() }),
    onSuccess: (_, review) => {
      toast.success(t("rejected"));
      setRejecting(null);
      done(review);
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const reasonOk = reason.trim().length >= 5 && reason.trim().length <= 500;
  const busyId = approve.isPending ? approve.variables?.id : undefined;

  return (
    <div className="grid gap-4">
      {query.isPending && <HexLoader label={tCommon("loading")} />}
      {query.isError && <FormAlert>{errorMessage(query.error)}</FormAlert>}
      {query.data && query.data.totalElements === 0 && (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("empty")}</p>
      )}
      {query.data && query.data.totalElements > 0 && (
        <>
          <p className="text-sm text-muted-foreground">
            {t("waiting", { count: query.data.totalElements })}
          </p>
          <ul className="grid gap-3">
            {query.data.content.map((review) => (
              <li key={review.id}>
                <article className="grid gap-2 rounded-lg border bg-card p-4">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <h2 className="font-medium">
                      <Link
                        href={`/books/${review.bookId}`}
                        target="_blank"
                        className="underline-offset-4 hover:underline"
                      >
                        {review.bookTitle}
                      </Link>
                    </h2>
                    <time dateTime={review.updatedAt} className="text-xs text-muted-foreground">
                      {format.dateTime(new Date(review.updatedAt), {
                        dateStyle: "medium",
                        timeStyle: "short",
                      })}
                    </time>
                  </div>
                  <div className="flex flex-wrap items-center gap-3 text-sm">
                    <Stars rating={review.rating} />
                    <span>{review.reviewer}</span>
                  </div>
                  {review.text ? (
                    <p className="text-sm leading-relaxed whitespace-pre-line">{review.text}</p>
                  ) : (
                    <p className="text-sm text-muted-foreground italic">{t("noText")}</p>
                  )}
                  <div className="flex gap-2 pt-1">
                    <Button
                      size="sm"
                      disabled={busyId === review.id}
                      onClick={() => approve.mutate(review)}
                    >
                      <Check aria-hidden />
                      {t("approve")}
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => {
                        setReason("");
                        setRejecting(review);
                      }}
                    >
                      <X aria-hidden />
                      {t("reject")}
                    </Button>
                  </div>
                </article>
              </li>
            ))}
          </ul>
          <Pager page={page} totalPages={query.data.totalPages} onChange={setPage} />
        </>
      )}

      <Dialog open={rejecting !== null} onOpenChange={(open) => !open && setRejecting(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("rejectTitle")}</DialogTitle>
            <DialogDescription>{t("rejectText")}</DialogDescription>
          </DialogHeader>
          <div className="flex flex-wrap gap-2">
            {QUICK_REASONS.map((key) => (
              <Button
                key={key}
                type="button"
                size="sm"
                variant="secondary"
                onClick={() => setReason(t(`reasons.${key}`))}
              >
                {t(`reasonLabels.${key}`)}
              </Button>
            ))}
          </div>
          <label className="grid gap-1 text-sm">
            {t("reason")}
            <Textarea
              rows={3}
              maxLength={500}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejecting(null)}>
              {t("cancel")}
            </Button>
            <Button
              variant="destructive"
              disabled={!reasonOk || reject.isPending}
              onClick={() => rejecting && reject.mutate(rejecting)}
            >
              {t("reject")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
