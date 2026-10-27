"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { X } from "lucide-react";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Pager } from "@/features/staff/pager";
import { Link } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";

type ShelfRow = Schema<"HoldShelfSummary">;

/** Today as "2026-10-27" in the browser's calendar, to compare with pickup days. */
function todayIso(now: number) {
  const d = new Date(now);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * The hold shelf (HOLD-6, checklist step 264): every copy set aside for a member, the soonest
 * pickup deadline first, so staff can see what's waiting, call people whose last day is today,
 * and spot copies left past their deadline (tonight's job passes them on). A hold can be cancelled
 * here with a reason, e.g. the member phoned to say they no longer need it.
 */
export function HoldShelf() {
  const t = useTranslations("HoldShelf");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [now] = useState(() => Date.now());
  const [cancelling, setCancelling] = useState<ShelfRow | null>(null);
  const [reason, setReason] = useState("");

  const shelf = useQuery({
    queryKey: ["staff", "holds", "shelf", page],
    queryFn: () =>
      api.get<PageResponse<ShelfRow>>("/staff/holds/shelf", { query: { page, size: 50 } }),
    placeholderData: keepPreviousData,
    // Returns at the desk keep adding to it
    refetchInterval: 60_000,
  });

  const cancel = useMutation({
    mutationFn: ({ row, reason }: { row: ShelfRow; reason: string }) =>
      api.post(`/staff/holds/${row.holdId}/cancel`, { reason }),
    onSuccess: (_, { row }) => {
      setCancelling(null);
      setReason("");
      toast.success(t("cancelled", { title: row.title, memberNo: row.memberNo }));
      void queryClient.invalidateQueries({ queryKey: ["staff", "holds"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (shelf.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (shelf.isError) return <FormAlert>{errorMessage(shelf.error)}</FormAlert>;
  const data = shelf.data;
  if (data.totalElements === 0) {
    return <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("empty")}</p>;
  }

  const today = todayIso(now);
  const day = (iso: string) =>
    format.dateTime(new Date(`${iso}T00:00:00`), {
      weekday: "short",
      day: "numeric",
      month: "short",
    });

  return (
    <div className="grid gap-4">
      <p className="text-sm text-muted-foreground">{t("count", { count: data.totalElements })}</p>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("pickUpBy")}</TableHead>
            <TableHead>{t("book")}</TableHead>
            <TableHead>{t("member")}</TableHead>
            <TableHead>{t("readySince")}</TableHead>
            <TableHead>
              <span className="sr-only">{t("actions")}</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.content.map((row) => (
            <TableRow key={row.holdId}>
              <TableCell className="whitespace-nowrap">
                <div>{day(row.expiresAt)}</div>
                {row.pastDeadline ? (
                  <Badge variant="overdue">{t("pastDeadline")}</Badge>
                ) : row.expiresAt === today ? (
                  <Badge variant="on-loan">{t("lastDay")}</Badge>
                ) : null}
              </TableCell>
              <TableCell>
                <div className="font-medium">{row.title}</div>
                <div className="font-mono text-xs text-muted-foreground">
                  {row.copyBarcode}
                  {row.callNumber && <> · {row.callNumber}</>}
                </div>
              </TableCell>
              <TableCell>
                <Link
                  href={`/staff/members/${row.memberId}`}
                  className="font-medium underline-offset-4 hover:underline"
                >
                  {row.fullName}
                </Link>
                <div className="text-xs text-muted-foreground">
                  <span className="font-mono">{row.memberNo}</span>
                  {row.phone && (
                    <>
                      {" · "}
                      <a href={`tel:${row.phone}`} className="hover:underline">
                        {row.phone}
                      </a>
                    </>
                  )}
                </div>
              </TableCell>
              <TableCell className="text-sm whitespace-nowrap">
                {format.dateTime(new Date(row.readyAt), { dateStyle: "medium" })}
              </TableCell>
              <TableCell className="text-right">
                <Button variant="ghost" size="sm" onClick={() => setCancelling(row)}>
                  <X aria-hidden />
                  {t("cancel")}
                  <span className="sr-only">: {row.title}</span>
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <Pager page={page} totalPages={data.totalPages} onChange={setPage} />
      <p className="text-xs text-muted-foreground">{t("nightlyNote")}</p>

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
                cancel.mutate({ row: cancelling, reason: reason.trim() });
            }}
          >
            <DialogHeader>
              <DialogTitle>
                {t("cancelTitle", {
                  title: cancelling?.title ?? "",
                  memberNo: cancelling?.memberNo ?? "",
                })}
              </DialogTitle>
              <DialogDescription>{t("cancelText")}</DialogDescription>
            </DialogHeader>
            <div className="grid gap-1.5">
              <label htmlFor="cancel-reason" className="text-sm font-medium">
                {t("reason")}
              </label>
              <Textarea
                id="cancel-reason"
                rows={2}
                maxLength={500}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                placeholder={t("reasonPlaceholder")}
                required
                autoFocus
              />
            </div>
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
