"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { CircleCheck, CircleX } from "lucide-react";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Link } from "@/i18n/navigation";
import { ApiError, api } from "@/lib/api";
import { utf8Length } from "@/lib/forms";

/**
 * "Choose a password" from an emailed link. Two uses, one form:
 * - reset: forgot-password link -> POST /auth/reset-password
 * - invite: staff invitation -> POST /auth/accept-invite
 */
const MODES = {
  reset: {
    endpoint: "/auth/reset-password",
    passwordField: "newPassword",
    tokenCodes: { invalid: "RESET_TOKEN_INVALID", expired: "RESET_TOKEN_EXPIRED" },
  },
  invite: {
    endpoint: "/auth/accept-invite",
    passwordField: "password",
    tokenCodes: { invalid: "INVITE_TOKEN_INVALID", expired: "INVITE_TOKEN_EXPIRED" },
  },
} as const;

type Mode = keyof typeof MODES;
type Outcome = "done" | "invalid" | "expired";

export function SetPasswordForm({ mode }: { mode: Mode }) {
  const t = useTranslations("Auth.setPassword");
  const modeT = useTranslations(mode === "reset" ? "Auth.reset" : "Auth.invite");
  const v = useTranslations("Auth.validation");
  const errorsT = useTranslations("Auth.errors");
  const errorMessage = useAuthErrorMessage();
  const token = useSearchParams().get("token");
  const [formError, setFormError] = useState<string | null>(null);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const config = MODES[mode];

  const schema = z
    .object({
      password: z
        .string()
        .min(8, v("passwordMin"))
        .refine((value) => utf8Length(value) <= 72, v("passwordMax")),
      confirm: z.string(),
    })
    .refine((values) => values.password === values.confirm, {
      message: v("passwordsDontMatch"),
      path: ["confirm"],
    });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { password: "", confirm: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async ({ password }) => {
    setFormError(null);
    try {
      await api.post(config.endpoint, { token, [config.passwordField]: password });
      setOutcome("done");
    } catch (error) {
      if (error instanceof ApiError && error.code === config.tokenCodes.invalid) {
        setOutcome("invalid");
      } else if (error instanceof ApiError && error.code === config.tokenCodes.expired) {
        setOutcome("expired");
      } else if (error instanceof ApiError && error.isValidation && error.errors[0]) {
        // e.g. PASSWORD_TOO_COMMON: shown under the password field, in the user's language
        const { code, message } = error.errors[0];
        form.setError(
          "password",
          { message: errorsT.has(code as never) ? errorsT(code as never) : message },
          { shouldFocus: true },
        );
      } else {
        setFormError(errorMessage(error));
      }
    }
  });

  if (!token) {
    return <Result ok={false} title={t("missingTitle")} text={t("missingText")} />;
  }
  if (outcome === "done") {
    return (
      <Result ok title={modeT("doneTitle")} text={modeT("doneText")}>
        <Button asChild>
          <Link href="/login">{t("login")}</Link>
        </Button>
      </Result>
    );
  }
  if (outcome === "invalid" || outcome === "expired") {
    const title = outcome === "invalid" ? t("invalidTitle") : t("expiredTitle");
    // A reset can be requested again by the user; an invitation only by an admin
    return mode === "reset" ? (
      <Result ok={false} title={title} text={t("invalidText")}>
        <Button asChild>
          <Link href="/forgot-password">{t("newLink")}</Link>
        </Button>
      </Result>
    ) : (
      <Result ok={false} title={title} text={modeT("expiredText")} />
    );
  }

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      {formError && <FormAlert>{formError}</FormAlert>}
      <FormField label={t("password")} error={errors.password?.message} required>
        {(field) => (
          <Input
            type="password"
            autoComplete="new-password"
            {...field}
            {...form.register("password")}
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
      <Button type="submit" size="lg" disabled={isSubmitting}>
        {isSubmitting ? t("submitting") : t("submit")}
      </Button>
    </form>
  );
}

function Result({
  ok,
  title,
  text,
  children,
}: {
  ok: boolean;
  title: string;
  text: string;
  children?: React.ReactNode;
}) {
  const Icon = ok ? CircleCheck : CircleX;
  return (
    <div role="status" className="flex flex-col items-center gap-3 text-center">
      <Icon aria-hidden className={ok ? "size-10 text-available" : "size-10 text-destructive"} />
      <h2 className="font-sans text-lg font-semibold">{title}</h2>
      <p className="text-muted-foreground">{text}</p>
      {children && <div className="mt-2">{children}</div>}
    </div>
  );
}
