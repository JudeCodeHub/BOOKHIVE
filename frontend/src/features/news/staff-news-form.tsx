"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { RichTextEditor } from "@/components/forms/rich-text-editor";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage, useFieldErrors } from "@/features/auth/errors";
import { useRouter } from "@/i18n/navigation";
import { api } from "@/lib/api";
import { STAFF_NEWS_KEY, type StaffNewsPost } from "./staff-news";

/**
 * Write or edit a news post (checklist step 347). The body uses the shared Tiptap editor; the
 * API cleans the HTML again before saving (step 336). A new post is saved as a draft.
 */
export function StaffNewsForm({ post }: { post?: StaffNewsPost }) {
  const t = useTranslations("StaffNews");
  const v = useTranslations("Auth.validation");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);

  const schema = z.object({
    title: z.string().trim().min(1, v("required")).max(200, v("tooLong")),
    summary: z.string().trim().max(500, v("tooLong")),
    bodyHtml: z.string().min(1, t("bodyRequired")).max(100_000, v("tooLong")),
    pinned: z.boolean(),
  });
  type Values = z.infer<typeof schema>;

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: post?.title ?? "",
      summary: post?.summary ?? "",
      bodyHtml: post?.bodyHtml ?? "",
      pinned: post?.pinned ?? false,
    },
  });
  const { errors, isSubmitting, isDirty } = form.formState;

  const applyFieldErrors = useFieldErrors();
  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    const body = { ...values, summary: values.summary || undefined };
    try {
      if (post) {
        const saved = await api.put<StaffNewsPost>(`/staff/news/${post.id}`, body);
        queryClient.setQueryData([...STAFF_NEWS_KEY, "detail", post.id], saved);
        form.reset({ ...values, bodyHtml: saved.bodyHtml });
        toast.success(t("saved"));
      } else {
        const created = await api.post<StaffNewsPost>("/staff/news", body);
        toast.success(t("created"));
        router.push(`/staff/news/${created.id}`);
      }
      void queryClient.invalidateQueries({ queryKey: STAFF_NEWS_KEY });
    } catch (error) {
      if (!applyFieldErrors(error, form.setError)) setFormError(errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid max-w-3xl gap-5">
      {formError && <FormAlert>{formError}</FormAlert>}
      <fieldset disabled={isSubmitting} className="grid gap-5">
        <FormField label={t("title")} error={errors.title?.message} required>
          {(field) => <Input {...field} {...form.register("title")} />}
        </FormField>
        <FormField label={t("summary")} hint={t("summaryHint")} error={errors.summary?.message}>
          {(field) => (
            <Textarea rows={2} maxLength={500} {...field} {...form.register("summary")} />
          )}
        </FormField>
        <FormField label={t("body")} error={errors.bodyHtml?.message} required>
          {(field) => (
            <Controller
              control={form.control}
              name="bodyHtml"
              render={({ field: { value, onChange } }) => (
                <RichTextEditor {...field} label={t("body")} value={value} onChange={onChange} />
              )}
            />
          )}
        </FormField>
        <label className="flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            className="mt-0.5 size-4 accent-primary"
            {...form.register("pinned")}
          />
          <span>
            {t("pinnedLabel")}
            <span className="block text-muted-foreground">{t("pinnedHint")}</span>
          </span>
        </label>
      </fieldset>
      <div className="flex gap-2">
        <Button type="submit" disabled={isSubmitting || (post && !isDirty)}>
          {isSubmitting ? t("saving") : post ? t("save") : t("createDraft")}
        </Button>
      </div>
    </form>
  );
}
