"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { hasLocale, useLocale, useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { updateSessionUser } from "@/features/auth/session";
import { usePathname, useRouter } from "@/i18n/navigation";
import { LANGUAGES } from "@/i18n/languages";
import { routing } from "@/i18n/routing";
import { api, type Schema } from "@/lib/api";
import { applyServerErrors } from "@/lib/forms";

export type Profile = Schema<"ProfileResponse">;
export const PROFILE_QUERY_KEY = ["me"] as const;

/** Same rules as the backend's UpdateProfileRequest. */
const PHONE = /^(\+94|0)[0-9]{9}$/;

/** Edit name, phone and language (checklist step 117): GET /me, then PATCH /me. */
export function ProfileForm() {
  const t = useTranslations("Common");
  const tError = useTranslations("Error");
  const errorMessage = useAuthErrorMessage();
  const query = useQuery({
    queryKey: PROFILE_QUERY_KEY,
    queryFn: () => api.get<Profile>("/me"),
  });

  if (query.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={t("loading")} />
      </div>
    );
  }
  if (query.isError) {
    return (
      <FormAlert>
        {errorMessage(query.error)}{" "}
        <button type="button" onClick={() => void query.refetch()} className="underline">
          {tError("retry")}
        </button>
      </FormAlert>
    );
  }
  // key: start the form fresh if the saved profile changes underneath it
  return <ProfileFields key={query.dataUpdatedAt} profile={query.data} />;
}

function ProfileFields({ profile }: { profile: Profile }) {
  const t = useTranslations("Settings.profile");
  const v = useTranslations("Auth.validation");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const [formError, setFormError] = useState<string | null>(null);

  const schema = z.object({
    fullName: z.string().trim().min(1, v("required")).max(150, v("tooLong")),
    // Spaces are allowed while typing ("077 123 4567") and removed before checking
    phone: z
      .string()
      .transform((value) => value.replace(/\s+/g, ""))
      .refine((value) => value === "" || PHONE.test(value), v("phone")),
    preferredLanguage: z.enum(routing.locales),
  });
  type Values = z.input<typeof schema>;

  const form = useForm<Values, unknown, z.output<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: profile.fullName,
      phone: profile.phone ?? "",
      preferredLanguage: hasLocale(routing.locales, profile.preferredLanguage)
        ? profile.preferredLanguage
        : routing.defaultLocale,
    },
  });
  const { errors, isSubmitting, isDirty } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    try {
      // An empty phone ("") removes it
      const saved = await api.patch<Profile>("/me", values);
      queryClient.setQueryData(PROFILE_QUERY_KEY, saved);
      updateSessionUser({ fullName: saved.fullName, preferredLanguage: saved.preferredLanguage });
      toast.success(t("saved"));
      // A new language applies to the website too, not only to emails
      if (
        saved.preferredLanguage !== locale &&
        hasLocale(routing.locales, saved.preferredLanguage)
      ) {
        router.replace(pathname, { locale: saved.preferredLanguage });
      }
    } catch (error) {
      if (!applyServerErrors(error, form.setError)) {
        setFormError(errorMessage(error));
      }
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      {formError && <FormAlert>{formError}</FormAlert>}

      <FormField label={t("email")} hint={t("emailHint")}>
        {(field) => <Input type="email" value={profile.email} readOnly {...field} />}
      </FormField>

      <FormField label={t("fullName")} error={errors.fullName?.message} required>
        {(field) => <Input autoComplete="name" {...field} {...form.register("fullName")} />}
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

      <fieldset aria-describedby="language-hint">
        <legend className="text-sm font-medium">{t("language")}</legend>
        <p id="language-hint" className="mt-1 text-sm text-muted-foreground">
          {t("languageHint")}
        </p>
        <div className="mt-2 flex flex-wrap gap-2">
          {LANGUAGES.map((language) => (
            <label
              key={language.code}
              className="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm has-checked:border-primary has-checked:bg-primary/10 has-focus-visible:ring-3 has-focus-visible:ring-ring/50"
            >
              <input
                type="radio"
                value={language.code}
                className="accent-primary"
                {...form.register("preferredLanguage")}
              />
              <span lang={language.code}>{language.name}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <Button type="submit" className="justify-self-start" disabled={isSubmitting || !isDirty}>
        {isSubmitting ? t("submitting") : t("submit")}
      </Button>
    </form>
  );
}
