"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookmarkCheck, Hourglass, X } from "lucide-react";
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
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { BookCover } from "@/features/catalog/book-cover";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type HoldSummary = Schema<"HoldSummary">;

/**
 * "My holds" (checklist step 262): books ready to pick up first, with the copy's barcode and the
 * last day to collect it; then the books I'm queueing for, with my place in line. Each can be
 * cancelled (a copy set aside goes to the next person).
 */
export function MyHolds() {
  const t = useTranslations("MyHolds");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [cancelling, setCancelling] = useState<HoldSummary | null>(null);

  const holds = useQuery({
    queryKey: ["me", "holds"],
    queryFn: () => api.get<HoldSummary[]>("/me/holds"),
  });

  const cancel = useMutation({
    mutationFn: (hold: HoldSummary) => api.post(`/me/holds/${hold.holdId}/cancel`),
    onSuccess: (_, hold) => {
      setCancelling(null);
      toast.success(t("cancelled", { title: hold.title }));
      void queryClient.invalidateQueries({ queryKey: ["me", "holds"] });
      void queryClient.invalidateQueries({ queryKey: ["availability", hold.bookId] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (holds.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (holds.isError) {
    if (holds.error instanceof ApiError && holds.error.code === "MEMBER_NOT_FOUND") {
      return (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">
          {t.rich("notMember", {
            link: (chunks) => (
              <Link href="/account/membership" className="font-medium text-link underline">
                {chunks}
              </Link>
            ),
          })}
        </p>
      );
    }
    return <FormAlert>{errorMessage(holds.error)}</FormAlert>;
  }
  if (holds.data.length === 0) {
    return (
      <p className="rounded-lg border p-6 text-center text-muted-foreground">
        {t.rich("empty", {
          link: (chunks) => (
            <Link href="/search" className="font-medium text-link underline">
              {chunks}
            </Link>
          ),
        })}
      </p>
    );
  }

  const lastDay = (iso: string) =>
    format.dateTime(new Date(`${iso}T00:00:00`), {
      weekday: "long",
      day: "numeric",
      month: "long",
    });

  return (
    <div className="grid gap-4">
      <ul className="grid gap-3">
        {holds.data.map((hold) => {
          const ready = hold.status === "READY";
          return (
            <li
              key={hold.holdId}
              className={cn(
                "flex gap-4 rounded-xl border bg-card p-3 sm:p-4",
                ready && "border-primary bg-primary/5",
              )}
            >
              <Link href={`/books/${hold.bookId}`} className="shrink-0" tabIndex={-1} aria-hidden>
                <BookCover
                  bookId={hold.bookId}
                  version={hold.coverVersion}
                  alt=""
                  sizes="64px"
                  className="w-16"
                />
              </Link>
              <div className="grid min-w-0 flex-1 content-start gap-2">
                <h2 className="font-semibold">
                  <Link href={`/books/${hold.bookId}`} className="hover:underline">
                    {hold.title}
                  </Link>
                </h2>
                {ready ? (
                  <div className="grid gap-1 text-sm">
                    <p className="flex items-center gap-1.5 font-medium text-primary">
                      <BookmarkCheck aria-hidden className="size-4" />
                      {t("readyNow")}
                    </p>
                    {hold.expiresAt && <p>{t("pickUpBy", { date: lastDay(hold.expiresAt) })}</p>}
                    {hold.copyBarcode && (
                      <p className="text-muted-foreground">
                        {t("askFor")} <span className="font-mono">{hold.copyBarcode}</span>
                      </p>
                    )}
                  </div>
                ) : (
                  <div className="flex flex-wrap items-center gap-2 text-sm">
                    <Badge variant="on-loan">
                      <Hourglass aria-hidden />
                      {t("position", { position: hold.position ?? 1 })}
                    </Badge>
                    <span className="text-muted-foreground">
                      {t("queue", { count: hold.queueLength })}
                    </span>
                  </div>
                )}
                <p className="text-xs text-muted-foreground">
                  {t("placedOn", {
                    date: format.dateTime(new Date(hold.placedAt), { dateStyle: "medium" }),
                  })}
                  {hold.placedByStaff && <> · {t("placedAtDesk")}</>}
                </p>
                <div>
                  <Button variant="ghost" size="sm" onClick={() => setCancelling(hold)}>
                    <X aria-hidden />
                    {t("cancel")}
                    <span className="sr-only">: {hold.title}</span>
                  </Button>
                </div>
              </div>
            </li>
          );
        })}
      </ul>
      <p className="text-xs text-muted-foreground">{t("howItWorks")}</p>

      <Dialog open={cancelling !== null} onOpenChange={(open) => !open && setCancelling(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("cancelTitle", { title: cancelling?.title ?? "" })}</DialogTitle>
            <DialogDescription>
              {cancelling?.status === "READY" ? t("cancelReadyText") : t("cancelWaitingText")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancelling(null)}>
              {t("keep")}
            </Button>
            <Button
              variant="destructive"
              disabled={cancel.isPending}
              onClick={() => cancelling && cancel.mutate(cancelling)}
            >
              {t("cancelConfirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
