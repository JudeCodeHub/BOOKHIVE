"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { fromResponse, setSession, type AuthResponse } from "@/features/auth/session";
import { useRouter } from "@/i18n/navigation";
import { ApiError, api } from "@/lib/api";
import { applyServerErrors, utf8Length } from "@/lib/forms";

/**
 * Change password while logged in (checklist step 118): POST /me/password with the current one.
 * The server logs out every other device and gives this one a fresh session, which we keep.
 */
export function ChangePasswordForm() {
  const t = useTranslations("Settings.password");
  const v = useTranslations("Auth.validation");
  const errorsT = useTranslations("Auth.errors");
  const errorMessage = useAuthErrorMessage();
  const router = useRouter();
  const [formError, setFormError] = useState<string | null>(null);

  const schema = z
    .object({
      currentPassword: z.string().min(1, v("required")),
      newPassword: z
        .string()
        .min(8, v("passwordMin"))
        .refine((value) => utf8Length(value) <= 72, v("passwordMax")),
      confirm: z.string(),
    })
    .refine((values) => values.newPassword === values.confirm, {
      message: v("passwordsDontMatch"),
      path: ["confirm"],
    });
  type Values = z.infer<typeof schema>;

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: "", newPassword: "", confirm: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async ({ currentPassword, newPassword }) => {
    setFormError(null);
    try {
      const response = await api.post<AuthResponse>("/me/password", {
        currentPassword,
        newPassword,
      });
      setSession(fromResponse(response));
      toast.success(t("changed"));
      router.replace("/account/settings");
    } catch (error) {
      if (error instanceof ApiError && error.code === "CURRENT_PASSWORD_INCORRECT") {
        form.setError(
          "currentPassword",
          { message: errorsT("CURRENT_PASSWORD_INCORRECT") },
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

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      {formError && <FormAlert>{formError}</FormAlert>}

      <FormField label={t("current")} error={errors.currentPassword?.message} required>
        {(field) => (
          <Input
            type="password"
            autoComplete="current-password"
            {...field}
            {...form.register("currentPassword")}
          />
        )}
      </FormField>

      <FormField label={t("new")} hint={t("newHint")} error={errors.newPassword?.message} required>
        {(field) => (
          <Input
            type="password"
            autoComplete="new-password"
            {...field}
            {...form.register("newPassword")}
          />
        )}
      </FormField>

      <FormField label={t("confirm")} error={errors.confirm?.message} required>
        {(field) => (
          <Input
            type="password"
            autoComplete="new-password"
            {...field}
            {...form.register("confirm")}
          />
        )}
      </FormField>

      <Button type="submit" className="justify-self-start" disabled={isSubmitting}>
        {isSubmitting ? t("submitting") : t("submit")}
      </Button>
    </form>
  );
}
