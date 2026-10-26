"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { EyeOff, History } from "lucide-react";
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
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { BookCover } from "@/features/catalog/book-cover";
import { Pager } from "@/features/staff/pager";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type ReadingHistory = Schema<"ReadingHistoryResponse">;

/**
 * Reading history (checklist step 245): the books I've returned, newest first, and the switch to
 * stop keeping it (PRD §4.5). Turning it off hides the list at once; the library then removes my
 * name from returned loans after the days it has set. Turning it back on starts a new history.
 */
export function ReadingHistoryPage() {
  const t = useTranslations("ReadingHistory");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [confirmOff, setConfirmOff] = useState(false);

  const history = useQuery({
    queryKey: ["me", "loans", "history", page],
    queryFn: () => api.get<ReadingHistory>("/me/loans/history", { query: { page, size: 20 } }),
    placeholderData: keepPreviousData,
  });

  const toggle = useMutation({
    mutationFn: (enabled: boolean) =>
      api.put<{ enabled: boolean }>("/me/membership/reading-history", { enabled }),
    onSuccess: ({ enabled }) => {
      setConfirmOff(false);
      setPage(0);
      toast.success(enabled ? t("turnedOn") : t("turnedOff"));
      void queryClient.invalidateQueries({ queryKey: ["me", "loans", "history"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (history.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (history.isError) {
    if (history.error instanceof ApiError && history.error.code === "MEMBER_NOT_FOUND") {
      return (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">
          {t.rich("notMember", {
            link: (chunks) => (
              <Link href="/account/membership" className="font-medium text-primary underline">
                {chunks}
              </Link>
            ),
          })}
        </p>
      );
    }
    return <FormAlert>{errorMessage(history.error)}</FormAlert>;
  }

  const { historyEnabled, anonymiseAfterDays, loans } = history.data;
  const date = (iso: string) => format.dateTime(new Date(iso), { dateStyle: "medium" });

  return (
    <div className="grid gap-6">
      <section
        aria-labelledby="history-setting"
        className="flex flex-wrap items-start justify-between gap-4 rounded-xl border bg-card p-4"
      >
        <div className="grid max-w-prose gap-1">
          <h2 id="history-setting" className="font-semibold">
            {t("settingTitle")}
          </h2>
          <p className="text-sm text-muted-foreground">
            {historyEnabled
              ? t("settingOnHelp")
              : t("settingOffHelp", { days: anonymiseAfterDays })}
          </p>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={historyEnabled}
          aria-labelledby="history-setting"
          disabled={toggle.isPending}
          onClick={() => (historyEnabled ? setConfirmOff(true) : toggle.mutate(true))}
          className={cn(
            "relative inline-flex h-7 w-12 shrink-0 items-center rounded-full border-2 border-transparent transition-colors focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none disabled:opacity-50",
            historyEnabled ? "bg-primary" : "bg-muted-foreground/30",
          )}
        >
          <span
            aria-hidden
            className={cn(
              "block size-6 rounded-full bg-background shadow transition-transform",
              historyEnabled ? "translate-x-5" : "translate-x-0",
            )}
          />
        </button>
      </section>

      {!historyEnabled ? (
        <p className="flex flex-col items-center gap-2 rounded-lg border p-6 text-center text-muted-foreground">
          <EyeOff aria-hidden className="size-6" />
          {t("off")}
        </p>
      ) : loans.totalElements === 0 ? (
        <p className="flex flex-col items-center gap-2 rounded-lg border p-6 text-center text-muted-foreground">
          <History aria-hidden className="size-6" />
          {t("empty")}
        </p>
      ) : (
        <>
          <p className="text-sm text-muted-foreground">
            {t("count", { count: loans.totalElements })}
          </p>
          <ul className="grid gap-2">
            {loans.content.map((loan) => (
              <li
                key={loan.loanId}
                className="flex items-center gap-3 rounded-xl border bg-card p-3"
              >
                <BookCover
                  bookId={loan.bookId}
                  version={loan.coverVersion}
                  alt=""
                  sizes="48px"
                  className="w-12 shrink-0"
                />
                <div className="grid min-w-0 gap-0.5">
                  <Link href={`/books/${loan.bookId}`} className="font-medium hover:underline">
                    {loan.title}
                  </Link>
                  <p className="text-sm text-muted-foreground">
                    {t("borrowed", {
                      from: date(loan.checkedOutAt),
                      to: loan.returnedAt ? date(loan.returnedAt) : "",
                    })}
                  </p>
                </div>
              </li>
            ))}
          </ul>
          <Pager page={page} totalPages={loans.totalPages} onChange={setPage} />
        </>
      )}

      <Dialog open={confirmOff} onOpenChange={setConfirmOff}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("confirmTitle")}</DialogTitle>
            <DialogDescription>{t("confirmText", { days: anonymiseAfterDays })}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmOff(false)}>
              {t("keep")}
            </Button>
            <Button
              variant="destructive"
              disabled={toggle.isPending}
              onClick={() => toggle.mutate(false)}
            >
              {t("turnOff")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
