"use client";

import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { BookCheck, BookmarkCheck, CircleAlert, Info, PartyPopper } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { ScanInput } from "@/features/desk/scan-input";
import { playSound } from "@/features/desk/sounds";
import { ApiError, api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type ReturnResponse = Schema<"ReturnResponse">;

/** One scan at the returns desk and what came of it. */
type Row =
  | { id: number; barcode: string; state: "pending" }
  | { id: number; barcode: string; state: "done"; result: ReturnResponse }
  | { id: number; barcode: string; state: "notOnLoan" | "error"; message: string };

/** Rows kept on screen; older ones scroll away (the server has them all). */
const MAX_ROWS = 30;

/**
 * Returns mode (checklist step 240): scan books one after another without waiting. Each scan is
 * returned right away, and its row says where the book goes next: back to its shelf (with the
 * shelf and call number), or to the hold shelf for the member who reserved it. Late days and the
 * fine are shown; a book that was marked lost is welcomed back.
 */
export function ReturnsDesk() {
  const t = useTranslations("Desk");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const nextId = useRef(0);
  const [rows, setRows] = useState<Row[]>([]);

  const update = (id: number, row: Row) =>
    setRows((current) => current.map((r) => (r.id === id ? row : r)));

  const onScan = async (barcode: string) => {
    const id = nextId.current++;
    setRows((current) => [{ id, barcode, state: "pending" } as Row, ...current].slice(0, MAX_ROWS));
    try {
      const result = await api.post<ReturnResponse>("/staff/returns", { barcode });
      update(id, { id, barcode, state: "done", result });
      // The hold shelf gets its own, louder sound: that book must not go back on the shelf
      playSound(result.destination === "HOLD_SHELF" ? "hold" : "success");
      // Loan lists elsewhere in the console are now out of date
      void queryClient.invalidateQueries({ queryKey: ["staff", "loans"] });
      if (result.destination === "HOLD_SHELF") {
        void queryClient.invalidateQueries({ queryKey: ["staff", "holds"] });
      }
    } catch (error) {
      const code = error instanceof ApiError ? error.code : "";
      playSound(code === "COPY_NOT_ON_LOAN" ? "warning" : "error");
      if (code === "COPY_NOT_ON_LOAN") {
        update(id, { id, barcode, state: "notOnLoan", message: t("notOnLoan") });
      } else {
        update(id, {
          id,
          barcode,
          state: "error",
          message: code === "COPY_NOT_FOUND" ? t("unknownBarcode") : errorMessage(error),
        });
      }
    }
  };

  const returned = rows.filter((row) => row.state === "done").length;
  const money = (amount: number) =>
    format.number(amount, { style: "currency", currency: "LKR", currencyDisplay: "code" });

  return (
    <div className="grid gap-6">
      <ScanInput
        id="desk-scan"
        label={t("scanReturnLabel")}
        hint={t("scanReturnHint")}
        placeholder={t("placeholder")}
        onScan={onScan}
        busy={rows.some((row) => row.state === "pending")}
        className="max-w-2xl"
      />

      {rows.length === 0 ? (
        <p className="max-w-2xl rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">
          {t("returnsEmpty")}
        </p>
      ) : (
        <section aria-labelledby="desk-returns-title" className="grid max-w-2xl gap-3">
          <h2 id="desk-returns-title" className="text-sm text-muted-foreground">
            {t("returnedThisSession", { count: returned })}
          </h2>
          {/* The newest scan is read out; its routing is what staff act on next */}
          <ol className="grid gap-2" aria-live="polite">
            {rows.map((row, index) => (
              <ReturnRow key={row.id} row={row} latest={index === 0} money={money} />
            ))}
          </ol>
        </section>
      )}
    </div>
  );
}

function ReturnRow({
  row,
  latest,
  money,
}: {
  row: Row;
  latest: boolean;
  money: (amount: number) => string;
}) {
  const t = useTranslations("Desk");

  if (row.state === "pending") {
    return (
      <li className="flex items-center gap-3 rounded-xl border bg-card p-3">
        <HexLoader label={t("returning")} />
        <span className="font-mono text-sm">{row.barcode}</span>
      </li>
    );
  }

  if (row.state !== "done") {
    const warn = row.state === "notOnLoan";
    const Icon = warn ? Info : CircleAlert;
    return (
      <li
        className={cn(
          "flex items-start gap-3 rounded-xl border p-3",
          warn ? "border-on-loan/40 bg-on-loan/15" : "border-destructive/30 bg-destructive/10",
        )}
      >
        <Icon
          aria-hidden
          className={cn(
            "mt-0.5 size-5 shrink-0",
            warn ? "text-on-loan-foreground" : "text-destructive",
          )}
        />
        <div className="grid gap-0.5">
          <p className="font-mono text-sm">{row.barcode}</p>
          <p className={cn("text-sm", !warn && "text-destructive")}>{row.message}</p>
        </div>
      </li>
    );
  }

  const r = row.result;
  const hold = r.destination === "HOLD_SHELF";
  const Icon = hold ? BookmarkCheck : BookCheck;
  return (
    <li
      className={cn(
        "grid gap-2 rounded-xl border bg-card p-3",
        hold && "border-primary bg-primary/10",
        latest && "p-4 shadow-sm",
      )}
    >
      <div className="flex items-start gap-3">
        <Icon
          aria-hidden
          className={cn(
            "mt-0.5 shrink-0",
            latest ? "size-7" : "size-5",
            hold ? "text-primary" : "text-available-foreground",
          )}
        />
        <div className="grid min-w-0 gap-0.5">
          {/* Where it goes: the line staff act on, so it's the biggest */}
          <p className={cn("font-semibold", latest ? "text-xl" : "text-base")}>
            {hold
              ? t("toHoldShelf", { memberNo: r.heldForMemberNo })
              : r.shelfLocation
                ? t("toShelfAt", { shelf: r.shelfLocation })
                : t("toShelf")}
          </p>
          <p className="text-sm">
            {r.title}
            <span className="text-muted-foreground">
              {" · "}
              <span className="font-mono">{r.barcode}</span>
              {r.callNumber && <> · {r.callNumber}</>}
            </span>
          </p>
        </div>
      </div>

      {(r.wasLost || r.daysLate > 0) && (
        <ul className="grid gap-1 pl-10 text-sm">
          {r.wasLost && (
            <li className="flex items-center gap-1.5 text-available-foreground">
              <PartyPopper aria-hidden className="size-4" />
              {t("foundLost")}
            </li>
          )}
          {r.daysLate > 0 && (
            <li className={cn(r.fine > 0 ? "text-overdue-foreground" : "text-muted-foreground")}>
              {r.fine > 0
                ? t("lateWithFine", {
                    count: r.daysLate,
                    fine: money(r.fine),
                    memberNo: r.memberNo,
                  })
                : t("lateInGrace", { count: r.daysLate })}
            </li>
          )}
        </ul>
      )}
    </li>
  );
}
