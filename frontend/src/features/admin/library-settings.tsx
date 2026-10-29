"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { RotateCcw } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { ApiError, api, type Schema } from "@/lib/api";
import { applyServerErrors } from "@/lib/forms";

type Settings = Schema<"SettingsResponse">;
type Change = Schema<"SettingChangeSummary">;
type GroupKey = "loans" | "fines" | "holds" | "membership" | "notifications";

const QUERY_KEY = ["admin", "settings"] as const;

type FieldName =
  | "maxItemsPerCheckout"
  | "perDay"
  | "graceDays"
  | "maxPerItem"
  | "lostDefaultPrice"
  | "lostProcessingFee"
  | "blockBorrowingAt"
  | "librarianWaiveLimit"
  | "pickupOpenDays"
  | "validityYears"
  | "renewalWindowDays"
  | "documentRetentionDays"
  | "historyAnonymiseDays"
  | "dueSoonDaysBefore"
  | "holdExpiringDaysBefore"
  | "membershipExpiringDaysBefore";

/** One number on the page: its range matches the backend's check (Settings.java). */
type Field = {
  name: FieldName;
  unit: "items" | "days" | "years" | "lkr";
  min: number;
  max: number;
};

/** Most money an admin can type: far above any real fine, it only stops typos (an extra 0). */
const MAX_LKR = 100_000;

/** The groups and their fields, in page order (labels and hints come from the messages). */
const GROUPS: { key: GroupKey; fields: Field[] }[] = [
  { key: "loans", fields: [{ name: "maxItemsPerCheckout", unit: "items", min: 1, max: 50 }] },
  {
    key: "fines",
    fields: [
      { name: "perDay", unit: "lkr", min: 0, max: MAX_LKR },
      { name: "graceDays", unit: "days", min: 0, max: 30 },
      { name: "maxPerItem", unit: "lkr", min: 0, max: MAX_LKR },
      { name: "lostDefaultPrice", unit: "lkr", min: 0, max: MAX_LKR },
      { name: "lostProcessingFee", unit: "lkr", min: 0, max: MAX_LKR },
      { name: "blockBorrowingAt", unit: "lkr", min: 0, max: MAX_LKR },
      { name: "librarianWaiveLimit", unit: "lkr", min: 0, max: MAX_LKR },
    ],
  },
  { key: "holds", fields: [{ name: "pickupOpenDays", unit: "days", min: 1, max: 30 }] },
  {
    key: "membership",
    fields: [
      { name: "validityYears", unit: "years", min: 1, max: 10 },
      { name: "renewalWindowDays", unit: "days", min: 0, max: 365 },
      { name: "documentRetentionDays", unit: "days", min: 1, max: 3650 },
      { name: "historyAnonymiseDays", unit: "days", min: 0, max: 365 },
    ],
  },
  {
    key: "notifications",
    fields: [
      { name: "dueSoonDaysBefore", unit: "days", min: 0, max: 30 },
      { name: "holdExpiringDaysBefore", unit: "days", min: 0, max: 30 },
      { name: "membershipExpiringDaysBefore", unit: "days", min: 0, max: 365 },
    ],
  },
];

/**
 * Admin settings (ADM-1, checklist step 369): the library-wide rules, one card per group. Each
 * card saves on its own, with the version it was loaded at, so two admins can't overwrite each
 * other by accident; changes apply within 30 seconds and are audited.
 */
export function LibrarySettings() {
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: () => api.get<Settings>("/admin/settings"),
  });

  if (query.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (query.isError) return <FormAlert>{errorMessage(query.error)}</FormAlert>;

  return (
    <div className="grid gap-6">
      {GROUPS.map((group) => {
        const change = query.data.changes[group.key];
        return (
          <SettingsCard
            // A new version (saved here or reloaded) starts the form from the stored values
            key={`${group.key}-${change?.version}`}
            group={group.key}
            fields={group.fields}
            values={query.data[group.key] as unknown as Record<string, number>}
            change={change}
          />
        );
      })}
    </div>
  );
}

function SettingsCard({
  group,
  fields,
  values,
  change,
}: {
  group: GroupKey;
  fields: Field[];
  values: Record<string, number>;
  change: Change | undefined;
}) {
  const t = useTranslations("AdminSettings");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const [stale, setStale] = useState(false);

  const schema = z.object(
    Object.fromEntries(
      fields.map((f) => [
        f.name,
        z.coerce
          .number({ message: t("errors.number") })
          .min(f.min, t("errors.range", { min: f.min, max: f.max }))
          .max(f.max, t("errors.range", { min: f.min, max: f.max }))
          .refine(
            (n) => (f.unit === "lkr" ? Math.round(n * 100) === n * 100 : Number.isInteger(n)),
            f.unit === "lkr" ? t("errors.cents") : t("errors.whole"),
          ),
      ]),
    ),
  );
  // Typed text in, numbers out (z.coerce)
  const form = useForm<Record<string, unknown>, unknown, Record<string, number>>({
    resolver: zodResolver(schema),
    defaultValues: values,
  });
  const { errors, isDirty, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (body) => {
    setFormError(null);
    try {
      const saved = await api.put<Settings>(`/admin/settings/${group}`, body, {
        query: { version: change?.version ?? 0 },
      });
      toast.success(t("saved", { group: t(`groups.${group}.title`) }));
      queryClient.setQueryData(QUERY_KEY, saved);
      // The desk and the public rules page read some of these (e.g. items per checkout)
      void queryClient.invalidateQueries({ queryKey: ["library", "rules"] });
    } catch (error) {
      if (error instanceof ApiError && error.code === "SETTINGS_CHANGED") {
        setStale(true);
        return;
      }
      const translate = (fieldError: { code: string; field: string }) => {
        const f = fields.find((x) => x.name === fieldError.field);
        return f ? t("errors.range", { min: f.min, max: f.max }) : undefined;
      };
      if (!applyServerErrors(error, form.setError, translate)) setFormError(errorMessage(error));
    }
  });

  return (
    <section aria-labelledby={`settings-${group}`} className="rounded-lg border bg-card p-5">
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <div>
          <h2 id={`settings-${group}`} className="font-semibold">
            {t(`groups.${group}.title`)}
          </h2>
          <p className="text-sm text-muted-foreground">{t(`groups.${group}.description`)}</p>
        </div>
        {formError && <FormAlert>{formError}</FormAlert>}
        {stale && (
          <div
            role="alert"
            className="grid gap-2 rounded-md border border-on-loan/40 bg-on-loan/10 p-3 text-sm"
          >
            <p>{t("stale")}</p>
            <Button
              type="button"
              size="sm"
              variant="outline"
              className="justify-self-start"
              onClick={() => void queryClient.invalidateQueries({ queryKey: QUERY_KEY })}
            >
              {t("reload")}
            </Button>
          </div>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          {fields.map((f) => (
            <FormField
              key={f.name}
              label={t(`fields.${f.name}.label`)}
              hint={t(`fields.${f.name}.hint`)}
              error={errors[f.name]?.message as string | undefined}
              required
            >
              {(field) => (
                <div className="flex items-center gap-2">
                  {f.unit === "lkr" && <span className="text-sm text-muted-foreground">LKR</span>}
                  <Input
                    type="number"
                    inputMode={f.unit === "lkr" ? "decimal" : "numeric"}
                    min={f.min}
                    max={f.max}
                    step={f.unit === "lkr" ? "0.01" : "1"}
                    className="max-w-36"
                    {...field}
                    {...form.register(f.name)}
                  />
                  {f.unit !== "lkr" && (
                    <span className="text-sm text-muted-foreground">{t(`units.${f.unit}`)}</span>
                  )}
                </div>
              )}
            </FormField>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-3 border-t pt-4">
          <Button type="submit" disabled={!isDirty || isSubmitting || stale}>
            {isSubmitting ? t("saving") : t("save")}
          </Button>
          {isDirty && (
            <Button type="button" variant="ghost" onClick={() => form.reset(values)}>
              <RotateCcw aria-hidden />
              {t("undo")}
            </Button>
          )}
          <p className="ml-auto text-xs text-muted-foreground">
            {change?.updatedByName
              ? t("lastChanged", {
                  name: change.updatedByName,
                  date: format.dateTime(new Date(change.updatedAt), {
                    dateStyle: "medium",
                    timeStyle: "short",
                  }),
                })
              : t("neverChanged")}
          </p>
        </div>
      </form>
    </section>
  );
}
