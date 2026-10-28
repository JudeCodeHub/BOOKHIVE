"use client";

import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ShieldCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { api, type Schema } from "@/lib/api";

type Preference = Schema<"PreferenceSummary">;
type Channel = Preference["available"][number];
/** The kinds of message a member can switch off (the only ones this page receives). */
type OptionalType =
  | "MEMBERSHIP_EXPIRING"
  | "DUE_SOON"
  | "OVERDUE"
  | "HOLD_READY"
  | "HOLD_EXPIRING"
  | "HOLD_EXPIRED"
  | "EVENT_REMINDER";

const CHANNELS = [
  { channel: "EMAIL", field: "email" },
  { channel: "SMS", field: "sms" },
  { channel: "IN_APP", field: "inApp" },
] as const satisfies readonly { channel: Channel; field: "email" | "sms" | "inApp" }[];

/** Messages that always go out (security, decisions, receipts): listed so nobody looks for them. */
const ALWAYS_SENT = [
  "PASSWORD_RESET",
  "EMAIL_VERIFICATION",
  "MEMBERSHIP_APPROVED",
  "CHECKOUT_RECEIPT",
  "PAYMENT_RECEIVED",
] as const;

/**
 * Notification settings (NOTIF-5, checklist step 323): for each kind of reminder, which channels
 * I want. Only the channels a kind can use get a tick box; security messages and receipts are
 * listed as always sent. Saved with one button, so nothing changes by accident.
 */
export function NotificationSettings() {
  const t = useTranslations("NotificationSettings");
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<Preference[] | null>(null);

  const saved = useQuery({
    queryKey: ["me", "notification-preferences"],
    queryFn: () => api.get<Preference[]>("/me/notification-preferences"),
  });
  // Start editing from what's saved (and again after saving)
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- copy server data into the form
    if (saved.data) setDraft(saved.data);
  }, [saved.data]);

  const save = useMutation({
    mutationFn: (choices: Preference[]) =>
      api.put<Preference[]>("/me/notification-preferences", {
        choices: choices.map((p) => ({
          type: p.type,
          email: !!p.email,
          sms: !!p.sms,
          inApp: !!p.inApp,
        })),
      }),
    onSuccess: (data) => {
      queryClient.setQueryData(["me", "notification-preferences"], data);
      toast.success(t("saved"));
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (saved.isPending || !draft) {
    return saved.isError ? (
      <FormAlert>{errorMessage(saved.error)}</FormAlert>
    ) : (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }

  const changed = JSON.stringify(draft) !== JSON.stringify(saved.data);
  const name = (type: Preference["type"]) => t(`types.${type as OptionalType}.name`);
  const toggle = (type: Preference["type"], field: "email" | "sms" | "inApp") =>
    setDraft((current) =>
      (current ?? []).map((p) => (p.type === type ? { ...p, [field]: !p[field] } : p)),
    );

  return (
    <form
      className="grid gap-6"
      onSubmit={(event) => {
        event.preventDefault();
        save.mutate(draft);
      }}
    >
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("message")}</TableHead>
            {CHANNELS.map(({ channel }) => (
              <TableHead key={channel} className="text-center">
                {t(`channel.${channel}`)}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {draft.map((p) => (
            <TableRow key={p.type}>
              <TableCell>
                <div className="font-medium">{name(p.type)}</div>
                <div className="text-xs text-muted-foreground">
                  {t(`types.${p.type as OptionalType}.about`)}
                </div>
              </TableCell>
              {CHANNELS.map(({ channel, field }) => (
                <TableCell key={channel} className="text-center">
                  {p.available.includes(channel) ? (
                    <input
                      type="checkbox"
                      className="size-4 accent-primary"
                      checked={!!p[field]}
                      onChange={() => toggle(p.type, field)}
                      aria-label={t("toggle", {
                        message: name(p.type),
                        channel: t(`channel.${channel}`),
                      })}
                    />
                  ) : (
                    <span aria-label={t("notAvailable")} className="text-muted-foreground">
                      –
                    </span>
                  )}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <p className="text-xs text-muted-foreground">{t("smsNote")}</p>

      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={!changed || save.isPending}>
          {save.isPending ? t("saving") : t("save")}
        </Button>
        {changed && (
          <Button type="button" variant="ghost" onClick={() => setDraft(saved.data ?? null)}>
            {t("undo")}
          </Button>
        )}
      </div>

      <section aria-labelledby="always-sent" className="grid gap-2 rounded-lg border p-4">
        <h2 id="always-sent" className="flex items-center gap-2 font-medium">
          <ShieldCheck aria-hidden className="size-4 text-primary" />
          {t("alwaysTitle")}
        </h2>
        <p className="text-sm text-muted-foreground">{t("alwaysText")}</p>
        <ul className="list-disc pl-6 text-sm">
          {ALWAYS_SENT.map((type) => (
            <li key={type}>{t(`always.${type}`)}</li>
          ))}
        </ul>
      </section>
    </form>
  );
}
