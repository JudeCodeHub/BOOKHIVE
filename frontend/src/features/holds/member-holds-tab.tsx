"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookmarkCheck, BookmarkPlus, CircleAlert, Hourglass, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { type EntityChoice, EntityCombobox } from "@/components/forms/entity-combobox";
import { FormField } from "@/components/forms/form-field";
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
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { ApiError, api, type Schema } from "@/lib/api";

type HoldSummary = Schema<"HoldSummary">;
type HoldCheck = Schema<"HoldCheckResponse">;
type SearchResponse = Schema<"SearchResponse">;

/** Book titles matching what staff type, for the "place a hold" picker. */
async function searchBooks(text: string) {
  const found = await api.get<SearchResponse>("/search", { query: { q: text, size: 8 } });
  return found.results.content.map((book) => ({ id: book.id, name: book.title }));
}

/**
 * The Holds tab of a member's profile (checklist step 265): their open holds (ready first, then
 * queue positions), cancel with a reason, and placing a hold for them at the desk (step 258,
 * e.g. someone who phones in). The same rules apply as online; refusals list every reason.
 */
export function MemberHoldsTab({ memberId, memberNo }: { memberId: string; memberNo: string }) {
  const t = useTranslations("MemberHoldsTab");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [book, setBook] = useState<EntityChoice | null>(null);
  const [refused, setRefused] = useState<HoldCheck | null>(null);
  const [cancelling, setCancelling] = useState<HoldSummary | null>(null);
  const [reason, setReason] = useState("");

  const holds = useQuery({
    // Under ["staff", "holds"]: the hold shelf and this tab refresh together
    queryKey: ["staff", "holds", "member", memberId],
    queryFn: () => api.get<HoldSummary[]>(`/staff/members/${memberId}/holds`),
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["staff", "holds"] });

  const place = useMutation({
    mutationFn: (bookId: string) => api.post<HoldSummary>("/staff/holds", { memberNo, bookId }),
    onSuccess: (hold) => {
      setBook(null);
      setRefused(null);
      toast.success(t("placed", { title: hold.title, position: hold.position ?? 1 }));
      void refresh();
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === "HOLD_REFUSED") {
        setRefused((error.body.hold as HoldCheck | undefined) ?? null);
      } else {
        toast.error(errorMessage(error));
      }
    },
  });

  const cancel = useMutation({
    mutationFn: ({ hold, reason }: { hold: HoldSummary; reason: string }) =>
      api.post(`/staff/holds/${hold.holdId}/cancel`, { reason }),
    onSuccess: (_, { hold }) => {
      setCancelling(null);
      setReason("");
      toast.success(t("cancelled", { title: hold.title }));
      void refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const lastDay = (iso: string) =>
    format.dateTime(new Date(`${iso}T00:00:00`), {
      weekday: "short",
      day: "numeric",
      month: "short",
    });

  return (
    <div className="grid gap-6">
      <form
        className="grid max-w-xl gap-3 rounded-lg border p-4"
        onSubmit={(event) => {
          event.preventDefault();
          if (book?.id) place.mutate(book.id);
        }}
      >
        <FormField label={t("placeLabel")} hint={t("placeHint")}>
          {(field) => (
            <EntityCombobox
              {...field}
              value={book}
              onChange={(next) => {
                setBook(next);
                setRefused(null);
              }}
              search={searchBooks}
              queryKey="hold-books"
              placeholder={t("placePlaceholder")}
              allowCreate={false}
            />
          )}
        </FormField>
        {refused && (
          <ul
            role="alert"
            className="grid gap-1 rounded-lg border border-on-loan/40 bg-on-loan/15 px-3 py-2 text-sm"
          >
            {refused.problems.map((problem) => (
              <li key={problem.reason} className="flex items-start gap-1.5">
                <CircleAlert
                  aria-hidden
                  className="mt-0.5 size-4 shrink-0 text-on-loan-foreground"
                />
                {t(`problems.${problem.reason}`)}
              </li>
            ))}
          </ul>
        )}
        <div>
          <Button type="submit" size="sm" disabled={!book?.id || place.isPending}>
            <BookmarkPlus aria-hidden />
            {t("place")}
          </Button>
        </div>
      </form>

      {holds.isPending ? (
        <div className="flex justify-center py-6">
          <HexLoader label={tCommon("loading")} />
        </div>
      ) : holds.isError ? (
        <FormAlert>{errorMessage(holds.error)}</FormAlert>
      ) : holds.data.length === 0 ? (
        <p className="rounded-lg border p-6 text-center text-sm text-muted-foreground">
          {t("empty")}
        </p>
      ) : (
        <ul className="grid gap-2">
          {holds.data.map((hold) => (
            <li
              key={hold.holdId}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
            >
              <div className="grid min-w-0 gap-0.5">
                <p className="font-medium">{hold.title}</p>
                <p className="text-sm text-muted-foreground">
                  {t("placedOn", {
                    date: format.dateTime(new Date(hold.placedAt), { dateStyle: "medium" }),
                  })}
                  {hold.placedByStaff && <> · {t("atDesk")}</>}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {hold.status === "READY" ? (
                  <Badge variant="available">
                    <BookmarkCheck aria-hidden />
                    {t("ready", {
                      date: hold.expiresAt ? lastDay(hold.expiresAt) : "",
                      barcode: hold.copyBarcode ?? "",
                    })}
                  </Badge>
                ) : (
                  <Badge variant="on-loan">
                    <Hourglass aria-hidden />
                    {t("position", { position: hold.position ?? 1, total: hold.queueLength })}
                  </Badge>
                )}
                <Button variant="ghost" size="sm" onClick={() => setCancelling(hold)}>
                  <X aria-hidden />
                  {t("cancel")}
                  <span className="sr-only">: {hold.title}</span>
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Dialog
        open={cancelling !== null}
        onOpenChange={(open) => {
          if (!open) {
            setCancelling(null);
            setReason("");
          }
        }}
      >
        <DialogContent>
          <form
            className="grid gap-4"
            onSubmit={(event) => {
              event.preventDefault();
              if (cancelling && reason.trim())
                cancel.mutate({ hold: cancelling, reason: reason.trim() });
            }}
          >
            <DialogHeader>
              <DialogTitle>{t("cancelTitle", { title: cancelling?.title ?? "" })}</DialogTitle>
              <DialogDescription>{t("cancelText")}</DialogDescription>
            </DialogHeader>
            <FormField label={t("reason")} required>
              {(field) => (
                <Textarea
                  {...field}
                  rows={2}
                  maxLength={500}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  autoFocus
                />
              )}
            </FormField>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCancelling(null)}>
                {t("keep")}
              </Button>
              <Button
                type="submit"
                variant="destructive"
                disabled={!reason.trim() || cancel.isPending}
              >
                {t("cancelConfirm")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
