"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ExternalLink, RotateCcw } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { Controller, useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormField } from "@/components/forms/form-field";
import { RichTextEditor } from "@/components/forms/rich-text-editor";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage, useFieldErrors } from "@/features/auth/errors";
import { ApiError, api, type Schema } from "@/lib/api";

type LibraryInfo = Schema<"AdminLibraryInfoResponse">;

const QUERY_KEY = ["admin", "library-info"] as const;
const LANGUAGES = [
  { code: "En", name: "English" },
  { code: "Si", name: "සිංහල" },
  { code: "Ta", name: "தமிழ்" },
] as const;

/**
 * Library info (ADM-6, checklist step 377): the name, address, contact details, map pin and the
 * "about" text shown on the visit page. Saved with the version it was read at, so two admins
 * can't overwrite each other.
 */
export function LibraryInfoAdmin() {
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: () => api.get<LibraryInfo>("/admin/library-info"),
  });

  if (query.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (query.isError) return <FormAlert>{errorMessage(query.error)}</FormAlert>;
  // A new version (saved here or reloaded) starts the form from the stored values
  return <InfoForm key={query.data.version} info={query.data} />;
}

function InfoForm({ info }: { info: LibraryInfo }) {
  const t = useTranslations("AdminLibraryInfo");
  const v = useTranslations("Auth.validation");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const [stale, setStale] = useState(false);

  const schema = z.object({
    name: z.string().trim().min(1, v("required")).max(150, v("tooLong")),
    addressLine: z.string().trim().min(1, v("required")).max(200, v("tooLong")),
    city: z.string().trim().min(1, v("required")).max(80, v("tooLong")),
    postalCode: z.string().trim().max(10, v("tooLong")),
    phone: z.string().trim().min(1, v("required")).max(30, v("tooLong")),
    email: z.string().trim().min(1, v("required")).email(t("emailInvalid")).max(254, v("tooLong")),
    latitude: z.coerce
      .number({ message: t("numberInvalid") })
      .min(-90)
      .max(90),
    longitude: z.coerce
      .number({ message: t("numberInvalid") })
      .min(-180)
      .max(180),
    aboutEn: z.string().max(20_000, v("tooLong")),
    aboutSi: z.string().max(20_000, v("tooLong")),
    aboutTa: z.string().max(20_000, v("tooLong")),
  });
  // Typed text in (the coordinates arrive as text), checked numbers out
  type Typed = z.input<typeof schema>;
  type Values = z.output<typeof schema>;

  const form = useForm<Typed, unknown, Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: info.name,
      addressLine: info.addressLine,
      city: info.city,
      postalCode: info.postalCode ?? "",
      phone: info.phone,
      email: info.email,
      latitude: info.latitude,
      longitude: info.longitude,
      aboutEn: info.aboutEn,
      aboutSi: info.aboutSi,
      aboutTa: info.aboutTa,
    },
  });
  const { errors, isDirty, isSubmitting } = form.formState;
  const pin = useWatch({ control: form.control, name: ["latitude", "longitude"] }) as [
    number | string,
    number | string,
  ];

  const applyFieldErrors = useFieldErrors();
  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    try {
      const saved = await api.put<LibraryInfo>("/admin/library-info", values, {
        query: { version: info.version },
      });
      queryClient.setQueryData(QUERY_KEY, saved);
      // The visit page and the footer read these
      void queryClient.invalidateQueries({ queryKey: ["library", "info"] });
      toast.success(t("saved"));
    } catch (error) {
      if (error instanceof ApiError && error.code === "LIBRARY_INFO_CHANGED") {
        setStale(true);
        return;
      }
      if (!applyFieldErrors(error, form.setError)) setFormError(errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid max-w-3xl gap-6">
      {formError && <FormAlert>{formError}</FormAlert>}
      {stale && (
        <div
          role="alert"
          className="grid gap-2 rounded-md border border-on-loan/40 bg-on-loan/10 p-3 text-sm"
        >
          <p>{t("stale")}</p>
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="justify-self-start"
            onClick={() => void queryClient.invalidateQueries({ queryKey: QUERY_KEY })}
          >
            {t("reload")}
          </Button>
        </div>
      )}

      <section aria-labelledby="contact" className="grid gap-4 rounded-lg border bg-card p-5">
        <h2 id="contact" className="font-semibold">
          {t("contactTitle")}
        </h2>
        <FormField label={t("name")} error={errors.name?.message} required>
          {(field) => <Input {...field} {...form.register("name")} />}
        </FormField>
        <FormField label={t("addressLine")} error={errors.addressLine?.message} required>
          {(field) => <Input {...field} {...form.register("addressLine")} />}
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label={t("city")} error={errors.city?.message} required>
            {(field) => <Input {...field} {...form.register("city")} />}
          </FormField>
          <FormField label={t("postalCode")} error={errors.postalCode?.message}>
            {(field) => <Input inputMode="numeric" {...field} {...form.register("postalCode")} />}
          </FormField>
          <FormField
            label={t("phone")}
            hint={t("phoneHint")}
            error={errors.phone?.message}
            required
          >
            {(field) => <Input type="tel" {...field} {...form.register("phone")} />}
          </FormField>
          <FormField
            label={t("email")}
            hint={t("emailHint")}
            error={errors.email?.message}
            required
          >
            {(field) => <Input type="email" {...field} {...form.register("email")} />}
          </FormField>
        </div>
      </section>

      <section aria-labelledby="map" className="grid gap-4 rounded-lg border bg-card p-5">
        <div>
          <h2 id="map" className="font-semibold">
            {t("mapTitle")}
          </h2>
          <p className="text-sm text-muted-foreground">{t("mapHint")}</p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label={t("latitude")} error={errors.latitude?.message} required>
            {(field) => (
              <Input
                type="number"
                step="any"
                inputMode="decimal"
                {...field}
                {...form.register("latitude")}
              />
            )}
          </FormField>
          <FormField label={t("longitude")} error={errors.longitude?.message} required>
            {(field) => (
              <Input
                type="number"
                step="any"
                inputMode="decimal"
                {...field}
                {...form.register("longitude")}
              />
            )}
          </FormField>
        </div>
        <MapPreview latitude={Number(pin[0])} longitude={Number(pin[1])} />
      </section>

      <section aria-labelledby="about" className="grid gap-4 rounded-lg border bg-card p-5">
        <div>
          <h2 id="about" className="font-semibold">
            {t("aboutTitle")}
          </h2>
          <p className="text-sm text-muted-foreground">{t("aboutHint")}</p>
        </div>
        <Tabs defaultValue="En">
          <TabsList>
            {LANGUAGES.map((l) => (
              <TabsTrigger key={l.code} value={l.code}>
                {l.name}
              </TabsTrigger>
            ))}
          </TabsList>
          {LANGUAGES.map((l) => (
            <TabsContent key={l.code} value={l.code}>
              <Controller
                control={form.control}
                name={`about${l.code}`}
                render={({ field: { value, onChange } }) => (
                  <RichTextEditor
                    id={`about-${l.code}`}
                    label={`${t("aboutTitle")} (${l.name})`}
                    value={(value as string) ?? ""}
                    onChange={onChange}
                  />
                )}
              />
            </TabsContent>
          ))}
        </Tabs>
      </section>

      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" disabled={!isDirty || isSubmitting || stale}>
          {isSubmitting ? t("saving") : t("save")}
        </Button>
        {isDirty && (
          <Button type="button" variant="ghost" onClick={() => form.reset()}>
            <RotateCcw aria-hidden />
            {t("undo")}
          </Button>
        )}
        <p className="ml-auto text-xs text-muted-foreground">
          {info.updatedByName
            ? t("lastChanged", {
                name: info.updatedByName,
                date: format.dateTime(new Date(info.updatedAt), {
                  dateStyle: "medium",
                  timeStyle: "short",
                }),
              })
            : t("neverChanged")}
        </p>
      </div>
    </form>
  );
}

/** The same map the visit page shows, so the pin can be checked before saving. */
function MapPreview({ latitude, longitude }: { latitude: number; longitude: number }) {
  const t = useTranslations("AdminLibraryInfo");
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null;
  const bbox = [longitude - 0.004, latitude - 0.002, longitude + 0.004, latitude + 0.002]
    .map((n) => n.toFixed(5))
    .join(",");
  return (
    <div className="grid gap-2">
      <iframe
        title={t("mapPreview")}
        src={`https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${latitude},${longitude}`}
        className="aspect-video w-full rounded-lg border"
        loading="lazy"
        referrerPolicy="no-referrer"
      />
      <a
        href={`https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=17/${latitude}/${longitude}`}
        target="_blank"
        rel="noreferrer"
        className="inline-flex w-fit items-center gap-1 text-sm underline-offset-4 hover:underline"
      >
        {t("findCoordinates")}
        <ExternalLink aria-hidden className="size-3.5" />
      </a>
    </div>
  );
}
