"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, FileUp } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useForm, useWatch, type FieldPath } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { MEMBERSHIP_QUERY_KEY } from "@/features/membership/use-membership";
import { ApiError, api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type MembershipType = Schema<"MembershipTypeResponse">;

const STEPS = ["type", "details", "document", "review"] as const;
type Step = (typeof STEPS)[number];

// Same rules as the backend (MembershipApplicationService), checked early for quick feedback
const NIC = /^(\d{9}[VvXx]|\d{12})$/;
const MAX_FILE_BYTES = 5 * 1024 * 1024;
const FILE_TYPES = ["image/jpeg", "image/png", "image/webp", "application/pdf"];

/** Age in whole years today, from an ISO date (yyyy-mm-dd). */
function ageOn(dateOfBirth: string, today = new Date()) {
  const born = new Date(dateOfBirth);
  let age = today.getFullYear() - born.getFullYear();
  const birthdayPassed =
    today.getMonth() > born.getMonth() ||
    (today.getMonth() === born.getMonth() && today.getDate() >= born.getDate());
  if (!birthdayPassed) age -= 1;
  return age;
}

/**
 * Membership application in four steps (checklist step 138): type -> details -> document ->
 * check & send. One form underneath, so going back keeps what was typed; each step is checked
 * before moving on. Sent as multipart: the fields as JSON plus the document file.
 */
export function ApplicationForm() {
  const t = useTranslations("Membership");
  const tCommon = useTranslations("Common");
  const typesQuery = useQuery({
    queryKey: ["membership-types"],
    queryFn: () => api.get<MembershipType[]>("/memberships/types"),
  });

  if (typesQuery.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (typesQuery.isError) {
    return <FormAlert>{t("errors.MEMBERSHIP_TYPE_UNKNOWN")}</FormAlert>;
  }
  return <Wizard types={typesQuery.data} />;
}

function Wizard({ types }: { types: MembershipType[] }) {
  const t = useTranslations("Membership");
  const v = useTranslations("Auth.validation");
  const errorMessage = useAuthErrorMessage();
  const locale = useLocale();
  const queryClient = useQueryClient();
  const [step, setStep] = useState<Step>("type");
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const typeByCode = (code: string) => types.find((type) => type.code === code);

  const schema = z
    .object({
      membershipType: z.string().min(1, v("required")),
      dateOfBirth: z.string().min(1, v("required")),
      nic: z.string(),
      guardianMemberNo: z.string(),
      addressLine: z.string().trim().min(1, v("required")).max(200, v("tooLong")),
      city: z.string().trim().min(1, v("required")).max(80, v("tooLong")),
      postalCode: z
        .string()
        .trim()
        .refine((value) => value === "" || /^\d{5}$/.test(value), t("validation.postalCode")),
    })
    .superRefine((values, ctx) => {
      const type = typeByCode(values.membershipType);
      if (!type) return;
      if (values.dateOfBirth) {
        const age = ageOn(values.dateOfBirth);
        if (
          (type.minAge != null && age < type.minAge) ||
          (type.maxAge != null && age > type.maxAge)
        ) {
          ctx.addIssue({
            code: "custom",
            path: ["dateOfBirth"],
            message: t("validation.ageNotAllowed"),
          });
        }
      }
      if (type.requiredDocument === "NIC" && !NIC.test(values.nic.replace(/\s/g, ""))) {
        ctx.addIssue({ code: "custom", path: ["nic"], message: t("validation.nic") });
      }
      if (type.requiresGuardian && values.guardianMemberNo.trim() === "") {
        ctx.addIssue({ code: "custom", path: ["guardianMemberNo"], message: v("required") });
      }
    });
  type Values = z.infer<typeof schema>;

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      membershipType: "",
      dateOfBirth: "",
      nic: "",
      guardianMemberNo: "",
      addressLine: "",
      city: "",
      postalCode: "",
    },
  });
  const { errors, isSubmitting } = form.formState;
  const selected = typeByCode(useWatch({ control: form.control, name: "membershipType" }));
  const needsDocument = !!selected && selected.requiredDocument !== "NONE";
  // Types without a document skip that step
  const steps = STEPS.filter((s) => s !== "document" || needsDocument);
  const index = steps.indexOf(step);

  const fieldsOf: Record<Step, FieldPath<Values>[]> = {
    type: ["membershipType"],
    details: ["dateOfBirth", "nic", "guardianMemberNo", "addressLine", "city", "postalCode"],
    document: [],
    review: [],
  };

  function checkFile(): boolean {
    if (!needsDocument) return true;
    const problem = !file
      ? t("validation.document")
      : file.size > MAX_FILE_BYTES
        ? t("validation.fileTooLarge")
        : !FILE_TYPES.includes(file.type)
          ? t("validation.fileType")
          : null;
    setFileError(problem);
    return problem === null;
  }

  async function next() {
    const ok = step === "document" ? checkFile() : await form.trigger(fieldsOf[step]);
    if (ok) setStep(steps[index + 1]);
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    if (!checkFile()) {
      setStep("document");
      return;
    }
    const application = {
      membershipType: values.membershipType,
      dateOfBirth: values.dateOfBirth,
      nic: selected?.requiredDocument === "NIC" ? values.nic.replace(/\s/g, "") : undefined,
      guardianMemberNo: selected?.requiresGuardian ? values.guardianMemberNo.trim() : undefined,
      addressLine: values.addressLine,
      city: values.city,
      postalCode: values.postalCode || undefined,
    };
    const body = new FormData();
    // The JSON part needs its own content type so the backend reads it as JSON
    body.append(
      "application",
      new Blob([JSON.stringify(application)], { type: "application/json" }),
    );
    if (needsDocument && file) body.append("document", file);

    try {
      await api.post("/memberships/applications", body);
      toast.success(t("sent"));
      await queryClient.invalidateQueries({ queryKey: MEMBERSHIP_QUERY_KEY });
    } catch (error) {
      handleServerError(error);
    }
  });

  /** Puts the server's answer under the right field, and goes back to that step. */
  function handleServerError(error: unknown) {
    const code =
      error instanceof ApiError
        ? error.isValidation
          ? error.errors[0]?.code
          : error.code
        : undefined;
    const field =
      error instanceof ApiError && error.isValidation ? error.errors[0]?.field : undefined;
    const message =
      code && t.has(`errors.${code}` as never) ? t(`errors.${code}` as never) : errorMessage(error);
    if (field === "document" || code === "FILE_TOO_LARGE") {
      setFileError(message);
      setStep("document");
    } else if (field && field in form.getValues()) {
      form.setError(field as FieldPath<Values>, { message });
      setStep(field === "membershipType" ? "type" : "details");
    } else {
      setFormError(message);
    }
  }

  const typeName = (type: MembershipType) =>
    locale === "si" ? type.nameSi : locale === "ta" ? type.nameTa : type.nameEn;

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-6">
      {/* Step indicator */}
      <nav aria-label={t("steps.label")}>
        <p className="mb-2 text-sm text-muted-foreground">
          {t("steps.progress", { current: index + 1, total: steps.length })}
        </p>
        <ol className="flex flex-wrap gap-2">
          {steps.map((s, i) => (
            <li
              key={s}
              aria-current={s === step ? "step" : undefined}
              className={cn(
                "flex items-center gap-1.5 rounded-full border px-3 py-1 text-sm",
                s === step && "border-primary bg-primary/10 font-medium",
                i < index && "text-muted-foreground",
              )}
            >
              {i < index ? (
                <Check aria-hidden className="size-4 text-primary" />
              ) : (
                <span aria-hidden>{i + 1}.</span>
              )}
              {t(`steps.${s}`)}
            </li>
          ))}
        </ol>
      </nav>

      {formError && <FormAlert>{formError}</FormAlert>}

      {step === "type" && (
        <fieldset className="grid gap-3">
          <legend className="mb-3 font-medium">{t("type.legend")}</legend>
          {types.map((type) => (
            <label
              key={type.code}
              className="flex cursor-pointer gap-3 rounded-lg border p-4 has-checked:border-primary has-checked:bg-primary/5 has-focus-visible:ring-3 has-focus-visible:ring-ring/50"
            >
              <input
                type="radio"
                value={type.code}
                className="mt-1 accent-primary"
                {...form.register("membershipType")}
              />
              <span className="grid gap-1 text-sm">
                <span className="flex flex-wrap items-baseline gap-x-3">
                  <span className="text-base font-semibold">{typeName(type)}</span>
                  <span className="text-muted-foreground">
                    {t("type.age", { range: ageRange(type, t) })}
                  </span>
                  <span className="font-medium">
                    {Number(type.annualFee) === 0
                      ? t("type.free")
                      : t("type.fee", { fee: Number(type.annualFee) })}
                  </span>
                </span>
                <span>{t("type.items", { count: type.maxItems, days: type.loanDays })}</span>
                <span className="text-muted-foreground">
                  {type.requiredDocument === "NIC" && t("type.needsNic")}
                  {type.requiredDocument === "STUDENT_ID" && t("type.needsStudentId")}
                  {type.requiresGuardian && t("type.needsGuardian")}
                </span>
              </span>
            </label>
          ))}
          {errors.membershipType && (
            <p className="text-sm font-medium text-destructive">{errors.membershipType.message}</p>
          )}
        </fieldset>
      )}

      {step === "details" && (
        <div className="grid gap-4">
          <FormField label={t("details.dateOfBirth")} error={errors.dateOfBirth?.message} required>
            {(field) => (
              <Input type="date" autoComplete="bday" {...field} {...form.register("dateOfBirth")} />
            )}
          </FormField>
          {selected?.requiredDocument === "NIC" && (
            <FormField
              label={t("details.nic")}
              hint={t("details.nicHint")}
              error={errors.nic?.message}
              required
            >
              {(field) => <Input autoComplete="off" {...field} {...form.register("nic")} />}
            </FormField>
          )}
          {selected?.requiresGuardian && (
            <FormField
              label={t("details.guardian")}
              hint={t("details.guardianHint")}
              error={errors.guardianMemberNo?.message}
              required
            >
              {(field) => (
                <Input placeholder="BH-000123" {...field} {...form.register("guardianMemberNo")} />
              )}
            </FormField>
          )}
          <FormField label={t("details.address")} error={errors.addressLine?.message} required>
            {(field) => (
              <Input autoComplete="street-address" {...field} {...form.register("addressLine")} />
            )}
          </FormField>
          <div className="grid gap-4 sm:grid-cols-[2fr_1fr]">
            <FormField label={t("details.city")} error={errors.city?.message} required>
              {(field) => (
                <Input autoComplete="address-level2" {...field} {...form.register("city")} />
              )}
            </FormField>
            <FormField label={t("details.postalCode")} error={errors.postalCode?.message}>
              {(field) => (
                <Input
                  inputMode="numeric"
                  autoComplete="postal-code"
                  {...field}
                  {...form.register("postalCode")}
                />
              )}
            </FormField>
          </div>
        </div>
      )}

      {step === "document" && selected && (
        <FormField
          label={
            selected.requiredDocument === "NIC"
              ? t("document.nicTitle")
              : t("document.studentIdTitle")
          }
          hint={t("document.hint")}
          error={fileError ?? undefined}
          required
        >
          {(field) => (
            <div className="grid gap-2">
              <Input
                type="file"
                accept={FILE_TYPES.join(",")}
                {...field}
                onChange={(event) => {
                  setFile(event.target.files?.[0] ?? null);
                  setFileError(null);
                }}
              />
              {file && (
                <p className="flex items-center gap-2 text-sm">
                  <FileUp aria-hidden className="size-4 text-primary" />
                  {t("document.selected", { name: file.name })}
                </p>
              )}
              <p className="text-sm text-muted-foreground">{t("document.privacy")}</p>
            </div>
          )}
        </FormField>
      )}

      {step === "review" && selected && (
        <div className="grid gap-3">
          <p>{t("review.intro")}</p>
          <dl className="grid gap-x-6 gap-y-2 rounded-lg border p-4 text-sm sm:grid-cols-[auto_1fr]">
            <ReviewRow label={t("review.type")} value={typeName(selected)} />
            <ReviewRow label={t("details.dateOfBirth")} value={form.getValues("dateOfBirth")} />
            {selected.requiredDocument === "NIC" && (
              <ReviewRow label={t("details.nic")} value={form.getValues("nic")} />
            )}
            {selected.requiresGuardian && (
              <ReviewRow label={t("details.guardian")} value={form.getValues("guardianMemberNo")} />
            )}
            <ReviewRow
              label={t("details.address")}
              value={[
                form.getValues("addressLine"),
                form.getValues("city"),
                form.getValues("postalCode"),
              ]
                .filter(Boolean)
                .join(", ")}
            />
            <ReviewRow
              label={t("steps.document")}
              value={needsDocument ? (file?.name ?? "") : t("review.noDocument")}
            />
          </dl>
        </div>
      )}

      <div className="flex gap-3">
        {index > 0 && (
          <Button type="button" variant="secondary" onClick={() => setStep(steps[index - 1])}>
            {t("back")}
          </Button>
        )}
        {step === "review" ? (
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? t("submitting") : t("submit")}
          </Button>
        ) : (
          <Button type="button" onClick={() => void next()}>
            {t("next")}
          </Button>
        )}
      </div>
    </form>
  );
}

function ReviewRow({ label, value }: { label: string; value: string }) {
  return (
    <>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium break-words">{value}</dd>
    </>
  );
}

function ageRange(type: MembershipType, t: ReturnType<typeof useTranslations<"Membership">>) {
  if (type.minAge != null && type.maxAge != null) {
    return t("type.ageBetween", { min: type.minAge, max: type.maxAge });
  }
  if (type.maxAge != null) return t("type.ageUnder", { max: type.maxAge + 1 });
  return t("type.ageFrom", { min: type.minAge ?? 0 });
}
