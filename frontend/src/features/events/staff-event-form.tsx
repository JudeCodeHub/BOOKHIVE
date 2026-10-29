"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Controller, useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { RichTextEditor } from "@/components/forms/rich-text-editor";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage, useFieldErrors } from "@/features/auth/errors";
import { useRouter } from "@/i18n/navigation";
import { api } from "@/lib/api";
import { fromLibraryInput, toLibraryInput } from "./library-dates";
import { STAFF_EVENTS_KEY, type StaffEvent } from "./staff-events";

const AUDIENCES = ["ALL", "CHILDREN", "YOUNG_ADULT", "ADULT", "SENIOR"] as const;

/**
 * Create or edit an event (checklist step 344). Dates and times are typed in library time
 * (Colombo), whatever the staff member's computer is set to. A new event is saved as a draft.
 */
export function StaffEventForm({ event }: { event?: StaffEvent }) {
  const t = useTranslations("StaffEvents");
  const tAudience = useTranslations("Events.audience");
  const v = useTranslations("Auth.validation");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);

  const schema = z
    .object({
      title: z.string().trim().min(1, v("required")).max(200, v("tooLong")),
      description: z.string().max(20_000, v("tooLong")),
      startsAt: z.string().min(1, v("required")),
      endsAt: z.string().min(1, v("required")),
      venue: z.string().trim().min(1, v("required")).max(200, v("tooLong")),
      audience: z.enum(AUDIENCES),
      registrationRequired: z.boolean(),
      capacity: z
        .string()
        .trim()
        .refine(
          (s) => s === "" || (/^\d+$/.test(s) && +s >= 1 && +s <= 10_000),
          t("capacityInvalid"),
        ),
    })
    // datetime-local values compare correctly as text ("2026-11-20T10:00")
    .refine((values) => !values.startsAt || !values.endsAt || values.endsAt > values.startsAt, {
      path: ["endsAt"],
      message: t("endsBeforeStart"),
    });
  type Values = z.infer<typeof schema>;

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: event?.title ?? "",
      description: event?.description ?? "",
      startsAt: event ? toLibraryInput(event.startsAt) : "",
      endsAt: event ? toLibraryInput(event.endsAt) : "",
      venue: event?.venue ?? "",
      audience: event?.audience ?? "ALL",
      registrationRequired: event?.registrationRequired ?? true,
      capacity: event?.capacity != null ? String(event.capacity) : "",
    },
  });
  const { errors, isSubmitting, isDirty } = form.formState;
  const registrationRequired = useWatch({ control: form.control, name: "registrationRequired" });
  const locked = event?.status === "CANCELLED";

  const applyFieldErrors = useFieldErrors();
  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    const body = {
      title: values.title,
      description: values.description || undefined,
      startsAt: fromLibraryInput(values.startsAt),
      endsAt: fromLibraryInput(values.endsAt),
      venue: values.venue,
      audience: values.audience,
      registrationRequired: values.registrationRequired,
      capacity:
        values.registrationRequired && values.capacity !== "" ? Number(values.capacity) : undefined,
    };
    try {
      if (event) {
        const saved = await api.put<StaffEvent>(`/staff/events/${event.id}`, body);
        queryClient.setQueryData([...STAFF_EVENTS_KEY, "detail", event.id], saved);
        form.reset(form.getValues());
        toast.success(t("saved"));
      } else {
        const created = await api.post<StaffEvent>("/staff/events", body);
        toast.success(t("created"));
        router.push(`/staff/events/${created.id}`);
      }
      void queryClient.invalidateQueries({ queryKey: STAFF_EVENTS_KEY });
    } catch (error) {
      const translate = (field: { code: string }) =>
        t.has(`errors.${field.code}` as never) ? t(`errors.${field.code}` as never) : undefined;
      if (!applyFieldErrors(error, form.setError, translate)) setFormError(errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid max-w-3xl gap-5">
      {formError && <FormAlert>{formError}</FormAlert>}
      {locked && <p className="text-sm text-muted-foreground">{t("cancelledLocked")}</p>}
      <fieldset disabled={locked || isSubmitting} className="grid gap-5">
        <FormField label={t("title")} error={errors.title?.message} required>
          {(field) => <Input {...field} {...form.register("title")} />}
        </FormField>
        <FormField
          label={t("description")}
          hint={t("descriptionHint")}
          error={errors.description?.message}
        >
          {(field) => (
            <Controller
              control={form.control}
              name="description"
              render={({ field: { value, onChange } }) => (
                <RichTextEditor {...field} value={value} onChange={onChange} disabled={locked} />
              )}
            />
          )}
        </FormField>
        <div className="grid gap-5 sm:grid-cols-2">
          <FormField
            label={t("startsAt")}
            hint={t("timeHint")}
            error={errors.startsAt?.message}
            required
          >
            {(field) => <Input type="datetime-local" {...field} {...form.register("startsAt")} />}
          </FormField>
          <FormField label={t("endsAt")} error={errors.endsAt?.message} required>
            {(field) => <Input type="datetime-local" {...field} {...form.register("endsAt")} />}
          </FormField>
        </div>
        <div className="grid gap-5 sm:grid-cols-2">
          <FormField
            label={t("venue")}
            hint={t("venueHint")}
            error={errors.venue?.message}
            required
          >
            {(field) => <Input {...field} {...form.register("venue")} />}
          </FormField>
          <FormField label={t("audience")} error={errors.audience?.message}>
            {(field) => (
              <select
                className="h-8 rounded-lg border bg-transparent px-2.5 text-sm"
                {...field}
                {...form.register("audience")}
              >
                {AUDIENCES.map((a) => (
                  <option key={a} value={a}>
                    {tAudience(a)}
                  </option>
                ))}
              </select>
            )}
          </FormField>
        </div>
        <label className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            className="mt-0.5 size-4 accent-primary"
            {...form.register("registrationRequired")}
          />
          <span>
            {t("registrationRequired")}
            <span className="block text-muted-foreground">{t("registrationRequiredHint")}</span>
          </span>
        </label>
        {registrationRequired && (
          <FormField
            label={t("capacity")}
            hint={t("capacityHint")}
            error={errors.capacity?.message}
          >
            {(field) => (
              <Input
                type="number"
                inputMode="numeric"
                min={1}
                max={10_000}
                className="max-w-40"
                {...field}
                {...form.register("capacity")}
              />
            )}
          </FormField>
        )}
      </fieldset>
      {!locked && (
        <div className="flex gap-2">
          <Button type="submit" disabled={isSubmitting || (event && !isDirty)}>
            {isSubmitting ? t("saving") : event ? t("save") : t("createDraft")}
          </Button>
        </div>
      )}
    </form>
  );
}
