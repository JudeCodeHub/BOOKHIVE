"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { ApiError, api, type Schema } from "@/lib/api";

type Copy = Schema<"CopyResponse">;
type HistoryLine = Schema<"CopyHistoryResponse">;
type CopyDetailsRequest = Schema<"CopyDetailsRequest">;

const LENDING_TYPES = ["LENDABLE", "REFERENCE_ONLY", "RESTRICTED"] as const;
const CONDITIONS = ["NEW", "GOOD", "FAIR", "POOR", "DAMAGED"] as const;
const SOURCES = ["PURCHASE", "DONATION", "OTHER"] as const;

/**
 * One copy (checklist step 179): edit its details, withdraw it with a reason, and see its whole
 * history (CAT-9). Shown inside a dialog from the Copies tab.
 */
export function CopyDetail({ copy, onChanged }: { copy: Copy; onChanged: () => void }) {
  const t = useTranslations("CopyDetail");
  const tCopies = useTranslations("Copies");
  const tCommon = useTranslations("Common");
  const v = useTranslations("Auth.validation");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState<string | null>(null);

  const historyKey = ["staff", "copy-history", copy.id];
  const history = useQuery({
    queryKey: historyKey,
    queryFn: () => api.get<HistoryLine[]>(`/staff/copies/${copy.id}/history`),
  });

  const fail = (error: Error) => {
    const code = error instanceof ApiError ? error.code : "";
    toast.error(
      t.has(`errors.${code}` as never) ? t(`errors.${code}` as never) : errorMessage(error),
    );
  };
  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: historyKey });
    onChanged();
  };

  const optionalNumber = (value: unknown) =>
    value === "" || value === null || value === undefined ? undefined : Number(value);
  const schema = z.object({
    lendingType: z.enum(LENDING_TYPES),
    condition: z.enum(CONDITIONS),
    shelfLocation: z.string().trim().max(100, v("tooLong")),
    acquiredAt: z.string(),
    acquisitionSource: z.enum(SOURCES),
    price: z.number().min(0).optional(),
    notes: z.string().trim().max(500, v("tooLong")),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      lendingType: copy.lendingType,
      condition: copy.condition,
      shelfLocation: copy.shelfLocation ?? "",
      acquiredAt: copy.acquiredAt ?? "",
      acquisitionSource: copy.acquisitionSource,
      price: copy.price ?? undefined,
      notes: copy.notes ?? "",
    },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    const body: CopyDetailsRequest = {
      ...values,
      shelfLocation: values.shelfLocation || undefined,
      acquiredAt: values.acquiredAt || undefined,
      notes: values.notes || undefined,
      // Keep the donation link it already has
      donationId: copy.donationId ?? undefined,
    };
    try {
      await api.patch<Copy>(`/staff/copies/${copy.id}`, body);
      toast.success(t("edited"));
      await refresh();
    } catch (error) {
      fail(error as Error);
    }
  });

  const withdraw = useMutation({
    mutationFn: (text: string) =>
      api.post<Copy>(`/staff/copies/${copy.id}/withdraw`, { reason: text }),
    onSuccess: async () => {
      toast.success(t("withdrawn"));
      setReason("");
      await refresh();
    },
    onError: fail,
  });
  const canWithdraw = !["ON_LOAN", "ON_HOLD_SHELF", "WITHDRAWN"].includes(copy.status);
  const selectClass = "h-8 w-full rounded-lg border bg-transparent px-2.5 text-sm";
  const statusName = (status: HistoryLine["toStatus"]) => tCopies(`statuses.${status}`);

  return (
    <div className="grid gap-6">
      <DialogHeader>
        <DialogTitle className="font-mono">{t("title", { barcode: copy.barcode })}</DialogTitle>
      </DialogHeader>

      <form onSubmit={onSubmit} noValidate className="grid gap-3">
        <h3 className="font-medium">{t("details")}</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <FormField label={tCopies("lendingType")}>
            {(field) => (
              <select className={selectClass} {...field} {...form.register("lendingType")}>
                {LENDING_TYPES.map((value) => (
                  <option key={value} value={value}>
                    {tCopies(`lendingTypes.${value}`)}
                  </option>
                ))}
              </select>
            )}
          </FormField>
          <FormField label={tCopies("condition")}>
            {(field) => (
              <select className={selectClass} {...field} {...form.register("condition")}>
                {CONDITIONS.map((value) => (
                  <option key={value} value={value}>
                    {tCopies(`conditions.${value}`)}
                  </option>
                ))}
              </select>
            )}
          </FormField>
          <FormField label={tCopies("shelf")} error={errors.shelfLocation?.message}>
            {(field) => <Input {...field} {...form.register("shelfLocation")} />}
          </FormField>
          <FormField label={tCopies("acquiredAt")}>
            {(field) => <Input type="date" {...field} {...form.register("acquiredAt")} />}
          </FormField>
          <FormField label={tCopies("source")}>
            {(field) => (
              <select className={selectClass} {...field} {...form.register("acquisitionSource")}>
                {SOURCES.map((value) => (
                  <option key={value} value={value}>
                    {tCopies(`sources.${value}`)}
                  </option>
                ))}
              </select>
            )}
          </FormField>
          <FormField label={tCopies("price")} error={errors.price?.message}>
            {(field) => (
              <Input
                type="number"
                step="0.01"
                min={0}
                {...field}
                {...form.register("price", { setValueAs: optionalNumber })}
              />
            )}
          </FormField>
        </div>
        <FormField label={tCopies("notes")} error={errors.notes?.message}>
          {(field) => <Input {...field} {...form.register("notes")} />}
        </FormField>
        <Button type="submit" size="sm" className="justify-self-start" disabled={isSubmitting}>
          {isSubmitting ? t("editing") : t("edit")}
        </Button>
      </form>

      <section className="grid gap-2 rounded-lg border border-destructive/30 p-3">
        <h3 className="font-medium">{t("withdrawTitle")}</h3>
        <p className="text-sm text-muted-foreground">{t("withdrawHelp")}</p>
        {canWithdraw ? (
          <>
            <FormField label={t("withdrawReason")} error={reasonError ?? undefined} required>
              {(field) => (
                <Textarea
                  {...field}
                  rows={2}
                  maxLength={500}
                  value={reason}
                  onChange={(event) => {
                    setReason(event.target.value);
                    setReasonError(null);
                  }}
                />
              )}
            </FormField>
            <Button
              type="button"
              size="sm"
              variant="destructive"
              className="justify-self-start"
              disabled={withdraw.isPending}
              onClick={() =>
                reason.trim() ? withdraw.mutate(reason.trim()) : setReasonError(v("required"))
              }
            >
              {t("withdrawConfirm")}
            </Button>
          </>
        ) : (
          <p className="text-sm">
            {copy.status === "WITHDRAWN" ? t("errors.COPY_ALREADY_WITHDRAWN") : t("cannotWithdraw")}
          </p>
        )}
      </section>

      <section className="grid gap-2">
        <h3 className="font-medium">{t("history")}</h3>
        {history.isPending && <HexLoader label={tCommon("loading")} />}
        {history.data?.length === 0 && (
          <p className="text-sm text-muted-foreground">{t("historyEmpty")}</p>
        )}
        <ol className="grid gap-3 border-l pl-4">
          {history.data?.map((line, index) => (
            <li key={index} className="relative text-sm">
              <span
                aria-hidden
                className="absolute top-1.5 -left-[1.3rem] size-2 rounded-full bg-primary"
              />
              <p className="flex flex-wrap items-center gap-1 font-medium">
                {line.fromStatus ? (
                  <>
                    {statusName(line.fromStatus)}
                    <ArrowRight aria-hidden className="size-3" />
                    {statusName(line.toStatus)}
                  </>
                ) : (
                  `${t("added")}: ${statusName(line.toStatus)}`
                )}
              </p>
              {line.reason && <p>{line.reason}</p>}
              <p className="text-xs text-muted-foreground">
                <time dateTime={line.changedAt}>
                  {format.dateTime(new Date(line.changedAt), {
                    dateStyle: "medium",
                    timeStyle: "short",
                  })}
                </time>{" "}
                · {line.changedByName ? t("by", { name: line.changedByName }) : t("system")}
              </p>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
