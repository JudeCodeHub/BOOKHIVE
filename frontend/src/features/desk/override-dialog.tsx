"use client";

import { useState } from "react";
import { ShieldAlert } from "lucide-react";
import { useTranslations } from "next-intl";
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

/** Same limits as the backend (CheckoutRequest / RenewRequest). */
const MIN_REASON = 10;
const MAX_REASON = 500;

/**
 * Admin override (CIRC-7, checklist step 246): lend or renew despite rules that may be bent
 * (over the limit, unpaid fines, max renewals...). Lists exactly which rules are set aside and
 * requires a written reason; the server checks the admin role again and audits who, why and what.
 */
export function OverrideDialog({
  open,
  onOpenChange,
  title,
  rules,
  confirmLabel,
  pending,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /** The rules being set aside, already in the user's language */
  rules: string[];
  confirmLabel: string;
  pending: boolean;
  onConfirm: (reason: string) => void;
}) {
  const t = useTranslations("Override");
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);
  const length = reason.trim().length;
  const tooShort = length < MIN_REASON;

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          setReason("");
          setTouched(false);
        }
        onOpenChange(next);
      }}
    >
      <DialogContent>
        <form
          className="grid gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            setTouched(true);
            if (!tooShort) onConfirm(reason.trim());
          }}
        >
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ShieldAlert aria-hidden className="size-5 text-on-loan-foreground" />
              {title}
            </DialogTitle>
            <DialogDescription>{t("intro")}</DialogDescription>
          </DialogHeader>

          <ul className="grid gap-1.5 rounded-lg border border-on-loan/40 bg-on-loan/15 px-4 py-3 text-sm">
            {rules.map((rule) => (
              <li key={rule} className="ml-4 list-disc">
                {rule}
              </li>
            ))}
          </ul>

          <div className="grid gap-1.5">
            <label htmlFor="override-reason" className="text-sm font-medium">
              {t("reason")}
            </label>
            <Textarea
              id="override-reason"
              rows={3}
              maxLength={MAX_REASON}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder={t("reasonPlaceholder")}
              aria-invalid={touched && tooShort}
              aria-describedby="override-reason-hint"
              autoFocus
            />
            <p
              id="override-reason-hint"
              className={
                touched && tooShort ? "text-sm text-destructive" : "text-sm text-muted-foreground"
              }
            >
              {touched && tooShort
                ? t("reasonTooShort", { min: MIN_REASON })
                : t("reasonHint", { count: length, max: MAX_REASON })}
            </p>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t("cancel")}
            </Button>
            <Button type="submit" disabled={pending}>
              {pending ? t("working") : confirmLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
