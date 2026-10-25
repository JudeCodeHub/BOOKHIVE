"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { HexLoader } from "@/components/brand/hex-loader";
import { FormField } from "@/components/forms/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { ApiError, api, type Schema } from "@/lib/api";
import { applyServerErrors } from "@/lib/forms";

type MembershipType = Schema<"AdminMembershipTypeResponse">;
type Request = Schema<"MembershipTypeRequest">;

const QUERY_KEY = ["admin", "membership-types"] as const;
const DOCUMENTS = ["NIC", "STUDENT_ID", "NONE"] as const;

/** Admin: membership types and their borrowing rules (checklist step 145). */
export function MembershipTypesAdmin() {
  const t = useTranslations("AdminMembershipTypes");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  // undefined = dialog closed, null = creating, a type = editing it
  const [editing, setEditing] = useState<MembershipType | null | undefined>(undefined);

  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: () => api.get<MembershipType[]>("/admin/membership-types"),
  });

  if (query.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (query.isError) {
    return <FormAlert>{errorMessage(query.error)}</FormAlert>;
  }

  const ages = (type: MembershipType) => `${type.minAge ?? 0}–${type.maxAge ?? t("any")}`;

  return (
    <div className="grid gap-4">
      <Button className="justify-self-start" onClick={() => setEditing(null)}>
        <Plus aria-hidden />
        {t("new")}
      </Button>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("code")}</TableHead>
            <TableHead>{t("name")}</TableHead>
            <TableHead>{t("ages")}</TableHead>
            <TableHead>{t("rules")}</TableHead>
            <TableHead>{t("fee")}</TableHead>
            <TableHead>{t("document")}</TableHead>
            <TableHead>{t("status")}</TableHead>
            <TableHead>
              <span className="sr-only">{t("edit")}</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {query.data.map((type) => (
            <TableRow key={type.id}>
              <TableCell className="font-mono">{type.code}</TableCell>
              <TableCell>
                <div className="font-medium">{type.nameEn}</div>
                <div className="text-xs text-muted-foreground">
                  {type.nameSi} · {type.nameTa}
                </div>
              </TableCell>
              <TableCell>{ages(type)}</TableCell>
              <TableCell className="text-xs">
                {t("rulesSummary", {
                  items: type.maxItems,
                  days: type.loanDays,
                  renewals: type.maxRenewals,
                  holds: type.maxHolds,
                })}
              </TableCell>
              <TableCell>{format.number(Number(type.annualFee))}</TableCell>
              <TableCell>
                {t(`documents.${type.requiredDocument}`)}
                {type.requiresGuardian && (
                  <span className="block text-xs text-muted-foreground">{t("plusGuardian")}</span>
                )}
              </TableCell>
              <TableCell>
                <Badge variant={type.active ? "available" : "outline"}>
                  {type.active ? t("activeLabel") : t("retired")}
                </Badge>
              </TableCell>
              <TableCell className="text-right">
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`${t("edit")} ${type.nameEn}`}
                  onClick={() => setEditing(type)}
                >
                  <Pencil />
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Dialog open={editing !== undefined} onOpenChange={(open) => !open && setEditing(undefined)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          {editing !== undefined && (
            // key: a fresh form for each type opened
            <TypeForm
              key={editing?.id ?? "new"}
              type={editing}
              onDone={() => setEditing(undefined)}
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** Empty number inputs mean "no limit" (null), not 0. */
const optionalNumber = (value: unknown) =>
  value === "" || value === null || value === undefined ? null : Number(value);

function TypeForm({ type, onDone }: { type: MembershipType | null; onDone: () => void }) {
  const t = useTranslations("AdminMembershipTypes");
  const v = useTranslations("Auth.validation");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);

  const whole = (min: number, max: number) =>
    z
      .number({ error: v("required") })
      .int()
      .min(min)
      .max(max);
  const schema = z.object({
    code: type ? z.string() : z.string().regex(/^[A-Za-z][A-Za-z0-9_]{1,29}$/, v("required")),
    nameEn: z.string().trim().min(1, v("required")).max(60, v("tooLong")),
    nameSi: z.string().trim().min(1, v("required")).max(60, v("tooLong")),
    nameTa: z.string().trim().min(1, v("required")).max(60, v("tooLong")),
    minAge: whole(0, 120).nullable(),
    maxAge: whole(0, 120).nullable(),
    maxItems: whole(0, 50),
    loanDays: whole(1, 120),
    maxRenewals: whole(0, 20),
    maxHolds: whole(0, 20),
    annualFee: z
      .number({ error: v("required") })
      .min(0)
      .max(100000),
    requiredDocument: z.enum(DOCUMENTS),
    requiresGuardian: z.boolean(),
    allowedSections: z.string(),
    active: z.boolean(),
  });
  type Values = z.infer<typeof schema>;

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      code: type?.code ?? "",
      nameEn: type?.nameEn ?? "",
      nameSi: type?.nameSi ?? "",
      nameTa: type?.nameTa ?? "",
      minAge: type?.minAge ?? null,
      maxAge: type?.maxAge ?? null,
      maxItems: type?.maxItems ?? 4,
      loanDays: type?.loanDays ?? 14,
      maxRenewals: type?.maxRenewals ?? 2,
      maxHolds: type?.maxHolds ?? 3,
      annualFee: type ? Number(type.annualFee) : 0,
      requiredDocument: type?.requiredDocument ?? "NIC",
      requiresGuardian: type?.requiresGuardian ?? false,
      allowedSections: type?.allowedSections.join(", ") ?? "",
      active: type?.active ?? true,
    },
  });
  const { errors, isSubmitting } = form.formState;

  const save = useMutation({
    mutationFn: (body: Request) =>
      type
        ? api.put<MembershipType>(`/admin/membership-types/${type.id}`, body)
        : api.post<MembershipType>("/admin/membership-types", body),
    onSuccess: async () => {
      toast.success(type ? t("saved") : t("created"));
      await queryClient.invalidateQueries({ queryKey: QUERY_KEY });
      // The public list on the application form changes too
      await queryClient.invalidateQueries({ queryKey: ["membership-types"] });
      onDone();
    },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    const body: Request = {
      ...values,
      code: type ? undefined : values.code.toUpperCase(),
      minAge: values.minAge ?? undefined,
      maxAge: values.maxAge ?? undefined,
      allowedSections: values.allowedSections
        .split(",")
        .map((section) => section.trim().toUpperCase())
        .filter(Boolean),
    };
    try {
      await save.mutateAsync(body);
    } catch (error) {
      const code = error instanceof ApiError ? error.code : "";
      const translate = (fieldError: { code: string }) =>
        t.has(`errors.${fieldError.code}` as never)
          ? t(`errors.${fieldError.code}` as never)
          : undefined;
      if (code === "MEMBERSHIP_TYPE_EXISTS") {
        form.setError("code", { message: t("errors.MEMBERSHIP_TYPE_EXISTS") });
      } else if (!applyServerErrors(error, form.setError, translate)) {
        setFormError(errorMessage(error));
      }
    }
  });

  const numberField = (name: keyof Values, label: string, optional = false) => (
    <FormField
      label={label}
      error={errors[name]?.message}
      hint={optional ? t("ageHint") : undefined}
    >
      {(field) => (
        <Input
          type="number"
          inputMode="numeric"
          {...field}
          {...form.register(name, { setValueAs: optional ? optionalNumber : Number })}
        />
      )}
    </FormField>
  );

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>{type ? t("editTitle", { name: type.nameEn }) : t("createTitle")}</DialogTitle>
      </DialogHeader>
      {formError && <FormAlert>{formError}</FormAlert>}

      <FormField
        label={t("code")}
        hint={t("codeHint")}
        error={errors.code?.message}
        required={!type}
      >
        {(field) => (
          <Input
            className="font-mono uppercase"
            disabled={!!type}
            {...field}
            {...form.register("code")}
          />
        )}
      </FormField>
      <div className="grid gap-4 sm:grid-cols-3">
        {(["nameEn", "nameSi", "nameTa"] as const).map((name) => (
          <FormField key={name} label={t(name)} error={errors[name]?.message} required>
            {(field) => <Input {...field} {...form.register(name)} />}
          </FormField>
        ))}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        {numberField("minAge", t("minAge"), true)}
        {numberField("maxAge", t("maxAge"), true)}
      </div>
      <div className="grid gap-4 sm:grid-cols-4">
        {numberField("maxItems", t("maxItems"))}
        {numberField("loanDays", t("loanDays"))}
        {numberField("maxRenewals", t("maxRenewals"))}
        {numberField("maxHolds", t("maxHolds"))}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label={t("fee")} error={errors.annualFee?.message}>
          {(field) => (
            <Input
              type="number"
              step="0.01"
              inputMode="decimal"
              {...field}
              {...form.register("annualFee", { setValueAs: Number })}
            />
          )}
        </FormField>
        <FormField label={t("document")} error={errors.requiredDocument?.message}>
          {(field) => (
            <select
              className="h-8 rounded-lg border bg-transparent px-2.5 text-sm"
              {...field}
              {...form.register("requiredDocument")}
            >
              {DOCUMENTS.map((document) => (
                <option key={document} value={document}>
                  {t(`documents.${document}`)}
                </option>
              ))}
            </select>
          )}
        </FormField>
      </div>
      <FormField
        label={t("sections")}
        hint={t("sectionsHint")}
        error={errors.allowedSections?.message}
      >
        {(field) => (
          <Input className="uppercase" {...field} {...form.register("allowedSections")} />
        )}
      </FormField>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" className="accent-primary" {...form.register("requiresGuardian")} />
        {t("requiresGuardian")}
      </label>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" className="accent-primary" {...form.register("active")} />
        {t("active")}
      </label>

      <DialogFooter>
        <Button type="button" variant="secondary" onClick={onDone}>
          {t("cancel")}
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? t("saving") : t("save")}
        </Button>
      </DialogFooter>
    </form>
  );
}
