"use client";

import { useRef, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { CircleCheck } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Turnstile, type TurnstileHandle } from "@/components/forms/turnstile";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage, useFieldErrors } from "@/features/auth/errors";
import { ApiError, api } from "@/lib/api";

/** Contact form on the visit page (checklist step 207), protected by Turnstile and a rate limit. */
export function ContactForm() {
  const t = useTranslations("Contact");
  const v = useTranslations("Auth.validation");
  const errorMessage = useAuthErrorMessage();
  const locale = useLocale();
  const turnstile = useRef<TurnstileHandle>(null);
  const [token, setToken] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const schema = z.object({
    name: z.string().trim().min(1, v("required")).max(150, v("tooLong")),
    email: z
      .string()
      .trim()
      .min(1, v("required"))
      .max(254, v("tooLong"))
      .pipe(z.email(v("email"))),
    subject: z.string().trim().min(1, v("required")).max(200, v("tooLong")),
    message: z.string().trim().min(10, t("messageHint")).max(5000, v("tooLong")),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { name: "", email: "", subject: "", message: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const applyFieldErrors = useFieldErrors();
  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    if (!token) {
      setFormError(t("robot"));
      return;
    }
    try {
      await api.post("/contact", { ...values, language: locale, turnstileToken: token });
      setSent(true);
      form.reset();
    } catch (error) {
      const code =
        error instanceof ApiError ? (error.isValidation ? error.errors[0]?.code : error.code) : "";
      const translate = (c: string | undefined) =>
        c && t.has(`errors.${c}` as never) ? t(`errors.${c}` as never) : undefined;
      // The robot check isn't a visible field: show it above the form; the rest under their boxes
      if (
        code === "CAPTCHA_FAILED" ||
        !applyFieldErrors(error, form.setError, ({ code: c }) => translate(c))
      ) {
        setFormError(translate(code) ?? errorMessage(error));
      }
    } finally {
      // A token is good for one try only
      turnstile.current?.reset();
    }
  });

  if (sent) {
    return (
      <div role="status" className="flex items-start gap-3 rounded-lg border p-4">
        <CircleCheck aria-hidden className="mt-0.5 size-5 shrink-0 text-available-foreground" />
        <p>{t("sent")}</p>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      <p className="text-sm text-muted-foreground">{t("intro")}</p>
      {formError && <FormAlert>{formError}</FormAlert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label={t("name")} error={errors.name?.message} required>
          {(field) => <Input autoComplete="name" {...field} {...form.register("name")} />}
        </FormField>
        <FormField label={t("email")} error={errors.email?.message} required>
          {(field) => (
            <Input type="email" autoComplete="email" {...field} {...form.register("email")} />
          )}
        </FormField>
      </div>
      <FormField label={t("subject")} error={errors.subject?.message} required>
        {(field) => <Input {...field} {...form.register("subject")} />}
      </FormField>
      <FormField
        label={t("message")}
        hint={t("messageHint")}
        error={errors.message?.message}
        required
      >
        {(field) => <Textarea rows={5} {...field} {...form.register("message")} />}
      </FormField>
      <Turnstile ref={turnstile} onToken={setToken} />
      <Button type="submit" className="justify-self-start" disabled={isSubmitting}>
        {isSubmitting ? t("sending") : t("send")}
      </Button>
    </form>
  );
}
