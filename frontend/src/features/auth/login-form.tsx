"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { afterLoginPath } from "@/features/auth/redirects";
import { login } from "@/features/auth/session";
import { Link, useRouter } from "@/i18n/navigation";
import { routing, type Locale } from "@/i18n/routing";

export function LoginForm() {
  const t = useTranslations("Auth.login");
  const v = useTranslations("Auth.validation");
  const errorMessage = useAuthErrorMessage();
  const router = useRouter();
  const next = useSearchParams().get("next");
  const [formError, setFormError] = useState<string | null>(null);

  // Login only checks that something was typed: the password rules apply when choosing one
  const schema = z.object({
    email: z
      .string()
      .trim()
      .min(1, v("required"))
      .pipe(z.email(v("email"))),
    password: z.string().min(1, v("required")),
  });
  type Values = z.infer<typeof schema>;

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async ({ email, password }) => {
    setFormError(null);
    try {
      const session = await login(email, password);
      toast.success(t("welcome", { name: session.user.fullName }));
      // Continue in the user's own language, wherever they were going
      const language = session.user.preferredLanguage;
      const locale = routing.locales.includes(language as Locale)
        ? (language as Locale)
        : undefined;
      router.replace(afterLoginPath(session.user, next), { locale });
    } catch (error) {
      setFormError(errorMessage(error));
      form.resetField("password");
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      {formError && <FormAlert>{formError}</FormAlert>}

      <FormField label={t("email")} error={errors.email?.message} required>
        {(field) => (
          <Input type="email" autoComplete="email" {...field} {...form.register("email")} />
        )}
      </FormField>

      <FormField label={t("password")} error={errors.password?.message} required>
        {(field) => (
          <Input
            type="password"
            autoComplete="current-password"
            {...field}
            {...form.register("password")}
          />
        )}
      </FormField>

      <div className="-mt-2 text-right text-sm">
        <Link
          href="/forgot-password"
          className="text-muted-foreground underline-offset-4 hover:underline"
        >
          {t("forgot")}
        </Link>
      </div>

      <Button type="submit" size="lg" disabled={isSubmitting}>
        {isSubmitting ? t("submitting") : t("submit")}
      </Button>
    </form>
  );
}
