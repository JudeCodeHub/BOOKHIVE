"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { MailCheck } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Link } from "@/i18n/navigation";
import { ApiError, api } from "@/lib/api";
import { applyServerErrors, utf8Length } from "@/lib/forms";

/** Same limits as the backend's RegisterRequest, so most mistakes are caught before sending. */
const PASSWORD_MIN = 8;
const PASSWORD_MAX_BYTES = 72; // bcrypt's limit
const PHONE = /^(\+94|0)[0-9]{9}$/;

export function RegisterForm() {
  const t = useTranslations("Auth.register");
  const v = useTranslations("Auth.validation");
  const errorsT = useTranslations("Auth.errors");
  const errorMessage = useAuthErrorMessage();
  const locale = useLocale();
  const [formError, setFormError] = useState<string | null>(null);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

  const schema = z.object({
    fullName: z.string().trim().min(1, v("required")).max(150, v("tooLong")),
    email: z
      .string()
      .trim()
      .min(1, v("required"))
      .max(254, v("tooLong"))
      .pipe(z.email(v("email"))),
    password: z
      .string()
      .min(PASSWORD_MIN, v("passwordMin"))
      .refine((value) => utf8Length(value) <= PASSWORD_MAX_BYTES, v("passwordMax")),
    // Spaces are allowed while typing ("077 123 4567") and removed before checking
    phone: z
      .string()
      .transform((value) => value.replace(/\s+/g, ""))
      .refine((value) => value === "" || PHONE.test(value), v("phone")),
  });
  type Values = z.input<typeof schema>;

  const form = useForm<Values, unknown, z.output<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: "", email: "", password: "", phone: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    try {
      await api.post("/auth/register", {
        fullName: values.fullName,
        email: values.email,
        password: values.password,
        phone: values.phone || undefined,
        // The language they're using now; they can change it later in their profile
        preferredLanguage: locale,
      });
      setRegisteredEmail(values.email);
    } catch (error) {
      if (error instanceof ApiError && error.code === "EMAIL_ALREADY_REGISTERED") {
        form.setError(
          "email",
          { message: errorsT("EMAIL_ALREADY_REGISTERED") },
          { shouldFocus: true },
        );
        return;
      }
      const translate = ({ code }: { code: string }) =>
        errorsT.has(code as never) ? errorsT(code as never) : undefined;
      if (!applyServerErrors(error, form.setError, translate)) {
        setFormError(errorMessage(error));
      }
    }
  });

  if (registeredEmail) {
    return (
      <div role="status" className="flex flex-col items-center gap-3 text-center">
        <MailCheck aria-hidden className="size-10 text-primary" />
        <h2 className="font-sans text-lg font-semibold">{t("checkEmailTitle")}</h2>
        <p className="text-muted-foreground">{t("checkEmailText", { email: registeredEmail })}</p>
        <Button asChild variant="secondary" className="mt-2">
          <Link href="/login">{t("login")}</Link>
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      {formError && <FormAlert>{formError}</FormAlert>}

      <FormField label={t("fullName")} error={errors.fullName?.message} required>
        {(field) => <Input autoComplete="name" {...field} {...form.register("fullName")} />}
      </FormField>

      <FormField label={t("email")} error={errors.email?.message} required>
        {(field) => (
          <Input type="email" autoComplete="email" {...field} {...form.register("email")} />
        )}
      </FormField>

      <FormField
        label={t("password")}
        hint={t("passwordHint")}
        error={errors.password?.message}
        required
      >
        {(field) => (
          <Input
            type="password"
            autoComplete="new-password"
            {...field}
            {...form.register("password")}
          />
        )}
      </FormField>

      <FormField label={t("phone")} hint={t("phoneHint")} error={errors.phone?.message}>
        {(field) => (
          <Input
            type="tel"
            autoComplete="tel"
            inputMode="tel"
            {...field}
            {...form.register("phone")}
          />
        )}
      </FormField>

      <Button type="submit" size="lg" disabled={isSubmitting}>
        {isSubmitting ? t("submitting") : t("submit")}
      </Button>
    </form>
  );
}
