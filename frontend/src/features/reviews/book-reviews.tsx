"use client";

import { useState } from "react";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Trash2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { Badge } from "@/components/ui/badge";
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
import { useAuthErrorMessage } from "@/features/auth/errors";
import { useSession } from "@/features/auth/use-session";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";
import { StarInput, Stars } from "./stars";

type BookReviews = Schema<"BookReviewsResponse">;
type MyReview = Schema<"MyReviewResponse">;
type MyReviewSummary = Schema<"MyReviewSummary">;

const PAGE_SIZE = 5;
const MAX_TEXT = 2000;

/**
 * Reviews on the book page (COMM-1, checklist step 348): the average rating, approved reviews
 * (newest first, "show more"), and the member's own review box. Reviews are only shown after
 * staff approve them; reviewers appear as "First L.".
 */
export function BookReviews({ bookId }: { bookId: string }) {
  const t = useTranslations("Reviews");
  const tCommon = useTranslations("Common");
  const format = useFormatter();

  const reviews = useInfiniteQuery({
    queryKey: ["reviews", bookId],
    queryFn: ({ pageParam }) =>
      api.get<BookReviews>(`/books/${bookId}/reviews`, {
        query: { page: pageParam, size: PAGE_SIZE },
      }),
    initialPageParam: 0,
    getNextPageParam: (last) =>
      last.reviews.page + 1 < last.reviews.totalPages ? last.reviews.page + 1 : undefined,
  });

  const first = reviews.data?.pages[0];
  const items = reviews.data?.pages.flatMap((p) => p.reviews.content) ?? [];

  return (
    <section aria-labelledby="reviews" className="grid gap-4">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <h2 id="reviews" className="font-semibold">
          {t("title")}
        </h2>
        {first && first.average != null && (
          <span className="flex items-center gap-2 text-sm">
            <Stars rating={first.average} />
            <span>
              {t("average", {
                average: format.number(first.average, { maximumFractionDigits: 1 }),
                count: first.count,
              })}
            </span>
          </span>
        )}
      </div>

      <MyReviewBox bookId={bookId} />

      {reviews.isPending && <HexLoader label={tCommon("loading")} />}
      {reviews.isSuccess && items.length === 0 && (
        <p className="text-sm text-muted-foreground">{t("none")}</p>
      )}
      {items.length > 0 && (
        <ul className="grid gap-4">
          {items.map((review) => (
            <li key={review.id} className="grid gap-1 border-b pb-4 last:border-0">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
                <Stars rating={review.rating} />
                <span className="font-medium">{review.reviewer}</span>
                <time dateTime={review.createdAt} className="text-muted-foreground">
                  {format.dateTime(new Date(review.createdAt), { dateStyle: "medium" })}
                </time>
              </div>
              {review.text && (
                <p className="text-sm leading-relaxed whitespace-pre-line">{review.text}</p>
              )}
            </li>
          ))}
        </ul>
      )}
      {reviews.hasNextPage && (
        <Button
          variant="outline"
          size="sm"
          className="justify-self-start"
          disabled={reviews.isFetchingNextPage}
          onClick={() => void reviews.fetchNextPage()}
        >
          {t("showMore")}
        </Button>
      )}
    </section>
  );
}

/** The logged-in member's review: write one (if they borrowed the book), see its status, change it. */
function MyReviewBox({ bookId }: { bookId: string }) {
  const t = useTranslations("Reviews");
  const session = useSession();
  const loggedIn = session.status === "authenticated";
  const [editing, setEditing] = useState(false);

  const mine = useQuery({
    queryKey: ["me", "reviews", bookId],
    queryFn: () => api.get<MyReview>(`/me/reviews/${bookId}`),
    enabled: loggedIn,
    retry: false,
  });

  if (session.status === "loading") return null;
  if (!loggedIn) {
    return (
      <p className="text-sm text-muted-foreground">
        {t.rich("logInToReview", {
          link: (chunks) => (
            <Link
              href={`/login?next=${encodeURIComponent(`/books/${bookId}`)}`}
              className="font-medium text-primary underline"
            >
              {chunks}
            </Link>
          ),
        })}
      </p>
    );
  }
  // Staff without a membership (MEMBER_NOT_FOUND) or a hiccup: the reviews still show, no box
  if (!mine.data) return null;

  const review = mine.data.review;
  if (review && !editing) {
    return <MyReviewStatus bookId={bookId} review={review} onEdit={() => setEditing(true)} />;
  }
  if (!review && !mine.data.canReview) {
    return <p className="text-sm text-muted-foreground">{t("borrowFirst")}</p>;
  }
  return (
    <ReviewForm
      bookId={bookId}
      review={review ?? undefined}
      onDone={() => setEditing(false)}
      onCancel={review ? () => setEditing(false) : undefined}
    />
  );
}

function MyReviewStatus({
  bookId,
  review,
  onEdit,
}: {
  bookId: string;
  review: MyReviewSummary;
  onEdit: () => void;
}) {
  const t = useTranslations("Reviews");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(false);

  const remove = useMutation({
    mutationFn: () => api.delete(`/me/reviews/${bookId}`),
    onSuccess: () => {
      setConfirming(false);
      toast.success(t("deleted"));
      void queryClient.invalidateQueries({ queryKey: ["me", "reviews", bookId] });
      void queryClient.invalidateQueries({ queryKey: ["reviews", bookId] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="grid gap-2 rounded-lg border bg-muted/30 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="text-sm font-semibold">{t("yourReview")}</h3>
        {review.status === "APPROVED" && <Badge variant="available">{t("status.APPROVED")}</Badge>}
        {review.status === "PENDING" && <Badge variant="on-loan">{t("status.PENDING")}</Badge>}
        {review.status === "REJECTED" && (
          <Badge variant="destructive">{t("status.REJECTED")}</Badge>
        )}
      </div>
      <Stars rating={review.rating} />
      {review.text && <p className="text-sm whitespace-pre-line">{review.text}</p>}
      {review.status === "PENDING" && (
        <p className="text-xs text-muted-foreground">{t("pendingText")}</p>
      )}
      {review.status === "REJECTED" && review.rejectReason && (
        <p className="text-sm">{t("rejectedText", { reason: review.rejectReason })}</p>
      )}
      <div className="flex gap-2">
        <Button size="sm" variant="secondary" onClick={onEdit}>
          <Pencil aria-hidden />
          {t("edit")}
        </Button>
        <Button size="sm" variant="ghost" onClick={() => setConfirming(true)}>
          <Trash2 aria-hidden />
          {t("delete")}
        </Button>
      </div>
      <Dialog open={confirming} onOpenChange={setConfirming}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("deleteTitle")}</DialogTitle>
            <DialogDescription>{t("deleteText")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirming(false)}>
              {t("keep")}
            </Button>
            <Button
              variant="destructive"
              disabled={remove.isPending}
              onClick={() => remove.mutate()}
            >
              {t("delete")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function ReviewForm({
  bookId,
  review,
  onDone,
  onCancel,
}: {
  bookId: string;
  review?: MyReviewSummary;
  onDone: () => void;
  onCancel?: () => void;
}) {
  const t = useTranslations("Reviews");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [rating, setRating] = useState(review?.rating ?? 0);
  const [text, setText] = useState(review?.text ?? "");
  const [missingRating, setMissingRating] = useState(false);

  const save = useMutation({
    mutationFn: () =>
      api.put<MyReviewSummary>(`/me/reviews/${bookId}`, {
        rating,
        text: text.trim() || undefined,
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData<MyReview>(["me", "reviews", bookId], (old) => ({
        canReview: old?.canReview ?? true,
        review: saved,
      }));
      // A changed review waits for staff again, so it may leave the public list
      void queryClient.invalidateQueries({ queryKey: ["reviews", bookId] });
      toast.success(t("sent"));
      onDone();
    },
    onError: (error) => {
      const code = error instanceof ApiError ? error.code : undefined;
      toast.error(code === "NOT_BORROWED" ? t("borrowFirst") : errorMessage(error));
    },
  });

  return (
    <form
      className="grid gap-3 rounded-lg border p-4"
      onSubmit={(event) => {
        event.preventDefault();
        if (rating < 1) {
          setMissingRating(true);
          return;
        }
        save.mutate();
      }}
    >
      <h3 className="text-sm font-semibold">{review ? t("editTitle") : t("writeTitle")}</h3>
      <StarInput
        value={rating}
        onChange={(n) => {
          setRating(n);
          setMissingRating(false);
        }}
        label={t("ratingLabel")}
        invalid={missingRating}
      />
      {missingRating && (
        <p role="alert" className="text-sm text-destructive">
          {t("ratingRequired")}
        </p>
      )}
      <label className="grid gap-1 text-sm">
        <span className="font-medium">{t("textLabel")}</span>
        <Textarea
          rows={4}
          maxLength={MAX_TEXT}
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder={t("textPlaceholder")}
        />
        <span className="text-xs text-muted-foreground">
          {t("textHint", { left: MAX_TEXT - text.length })}
        </span>
      </label>
      <div className="flex gap-2">
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? t("sending") : t("send")}
        </Button>
        {onCancel && (
          <Button type="button" variant="ghost" onClick={onCancel}>
            {t("cancel")}
          </Button>
        )}
      </div>
    </form>
  );
}
