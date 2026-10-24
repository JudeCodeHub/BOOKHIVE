"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { MailCheck } from "lucide-react";
import { useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Link } from "@/i18n/navigation";
import { api } from "@/lib/api";

/**
 * Asks for a reset link. The answer is the same whether or not the email has an account (the
 * backend always returns 204), so this page can't be used to find out who is a member.
 */
export function ForgotPasswordForm() {
  const t = useTranslations("Auth.forgot");
  const v = useTranslations("Auth.validation");
  const errorMessage = useAuthErrorMessage();
  const [formError, setFormError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);

  const schema = z.object({
    email: z
      .string()
      .trim()
      .min(1, v("required"))
      .pipe(z.email(v("email"))),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: "" } });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async ({ email }) => {
    setFormError(null);
    try {
      await api.post("/auth/forgot-password", { email });
      setSentTo(email);
    } catch (error) {
      setFormError(errorMessage(error));
    }
  });

  if (sentTo) {
    return (
      <div role="status" className="flex flex-col items-center gap-3 text-center">
        <MailCheck aria-hidden className="size-10 text-primary" />
        <h2 className="font-sans text-lg font-semibold">{t("sentTitle")}</h2>
        <p className="text-muted-foreground">{t("sentText", { email: sentTo })}</p>
        <Button asChild variant="secondary" className="mt-2">
          <Link href="/login">{t("backToLogin")}</Link>
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      {formError && <FormAlert>{formError}</FormAlert>}
      <FormField label={t("email")} error={errors.email?.message} required>
        {(field) => (
          <Input type="email" autoComplete="email" {...field} {...form.register("email")} />
        )}
      </FormField>
      <Button type="submit" size="lg" disabled={isSubmitting}>
        {isSubmitting ? t("submitting") : t("submit")}
      </Button>
    </form>
  );
}
