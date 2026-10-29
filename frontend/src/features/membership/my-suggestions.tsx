"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useFormatter, useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormField } from "@/components/forms/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage, useFieldErrors } from "@/features/auth/errors";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";

type Suggestion = Schema<"SuggestionResponse">;

const QUERY_KEY = ["me", "suggestions"] as const;
const STATUS_VARIANT = {
  SUBMITTED: "outline",
  ORDERED: "on-loan",
  ACQUIRED: "available",
  DECLINED: "secondary",
} as const;

/** A member suggests a book for the library to buy, and sees the answers (step 185, CAT-11). */
export function MySuggestions({ initialTitle }: { initialTitle?: string } = {}) {
  const t = useTranslations("MySuggestions");
  const v = useTranslations("Auth.validation");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);

  const mine = useQuery({
    queryKey: QUERY_KEY,
    queryFn: () => api.get<Suggestion[]>("/me/suggestions"),
  });

  const schema = z.object({
    title: z.string().trim().min(1, v("required")).max(300, v("tooLong")),
    author: z.string().trim().max(200, v("tooLong")),
    isbn: z.string().trim().max(20, v("tooLong")),
    reason: z.string().trim().max(1000, v("tooLong")),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    // From "Can't find it? Suggest it" on an empty search: the search text becomes the title
    defaultValues: { title: initialTitle ?? "", author: "", isbn: "", reason: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const applyFieldErrors = useFieldErrors();
  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    try {
      await api.post("/me/suggestions", {
        title: values.title,
        author: values.author || undefined,
        isbn: values.isbn || undefined,
        reason: values.reason || undefined,
      });
      toast.success(t("sent"));
      form.reset();
      await queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    } catch (error) {
      const translate = ({ code }: { code: string }) =>
        t.has(`errors.${code}` as never) ? t(`errors.${code}` as never) : undefined;
      if (!applyFieldErrors(error, form.setError, translate)) {
        const code = error instanceof ApiError ? error.code : "";
        setFormError(translate({ code }) ?? errorMessage(error));
      }
    }
  });

  return (
    <div className="grid max-w-2xl gap-8">
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <p className="text-muted-foreground">{t("intro")}</p>
        {formError && <FormAlert>{formError}</FormAlert>}
        <FormField label={t("bookTitle")} error={errors.title?.message} required>
          {(field) => <Input {...field} {...form.register("title")} />}
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label={t("author")} error={errors.author?.message}>
            {(field) => <Input {...field} {...form.register("author")} />}
          </FormField>
          <FormField label={t("isbn")} hint={t("isbnHint")} error={errors.isbn?.message}>
            {(field) => <Input inputMode="numeric" {...field} {...form.register("isbn")} />}
          </FormField>
        </div>
        <FormField label={t("reason")} hint={t("reasonHint")} error={errors.reason?.message}>
          {(field) => <Textarea rows={3} {...field} {...form.register("reason")} />}
        </FormField>
        <Button type="submit" className="justify-self-start" disabled={isSubmitting}>
          {isSubmitting ? t("submitting") : t("submit")}
        </Button>
      </form>

      <section aria-labelledby="my-suggestions" className="grid gap-3">
        <h2 id="my-suggestions" className="text-lg font-semibold">
          {t("mine")}
        </h2>
        {mine.isPending && <HexLoader label={tCommon("loading")} />}
        {mine.isError && <FormAlert>{errorMessage(mine.error)}</FormAlert>}
        {mine.data?.length === 0 && <p className="text-muted-foreground">{t("none")}</p>}
        <ul className="grid gap-3">
          {mine.data?.map((s) => (
            <li key={s.id} className="grid gap-1 rounded-lg border p-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-medium">{s.title}</span>
                {s.author && <span className="text-sm text-muted-foreground">{s.author}</span>}
                <Badge variant={STATUS_VARIANT[s.status]}>{t(`statuses.${s.status}`)}</Badge>
              </div>
              <p className="text-xs text-muted-foreground">
                {t("suggestedOn", {
                  date: format.dateTime(new Date(s.createdAt), { dateStyle: "medium" }),
                })}
              </p>
              {s.staffNote && <p className="text-sm">{t("note", { note: s.staffNote })}</p>}
              {s.bookId && (
                <Link href={`/books/${s.bookId}`} className="justify-self-start text-sm underline">
                  {t("openBook")}
                </Link>
              )}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
