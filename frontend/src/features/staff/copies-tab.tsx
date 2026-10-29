"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Printer } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useForm, useWatch } from "react-hook-form";
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
import { useAuthErrorMessage, useFieldErrors } from "@/features/auth/errors";
import { ApiError, api, type Schema } from "@/lib/api";
import { EntityCombobox, type EntityChoice } from "@/components/forms/entity-combobox";
import { searchDonations } from "@/features/staff/donations";
import { CopyDetail } from "@/features/staff/copy-detail";

type Copy = Schema<"CopyResponse">;
type AddCopiesRequest = Schema<"AddCopiesRequest">;

const LENDING_TYPES = ["LENDABLE", "REFERENCE_ONLY", "RESTRICTED"] as const;
const CONDITIONS = ["NEW", "GOOD", "FAIR", "POOR", "DAMAGED"] as const;
const SOURCES = ["PURCHASE", "DONATION", "OTHER"] as const;

// PRD §11 status colours: green on the shelf, amber out, red gone
const STATUS_VARIANT = {
  AVAILABLE: "available",
  ON_LOAN: "on-loan",
  ON_HOLD_SHELF: "reference",
  IN_TRANSIT: "outline",
  IN_REPAIR: "outline",
  LOST: "overdue",
  WITHDRAWN: "destructive",
} as const;

export const copiesQueryKey = (bookId: string) => ["staff", "copies", bookId] as const;

/**
 * A book's physical copies (checklist step 178): see them, add more, print barcode labels for
 * some or all of them.
 */
export function CopiesTab({ bookId }: { bookId: string }) {
  const t = useTranslations("Copies");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<string[]>([]);
  const [adding, setAdding] = useState(false);
  const [printing, setPrinting] = useState(false);
  const [openCopyId, setOpenCopyId] = useState<string | null>(null);

  const query = useQuery({
    queryKey: copiesQueryKey(bookId),
    queryFn: () => api.get<Copy[]>(`/staff/books/${bookId}/copies`),
  });

  /** Opens the label PDF in a new tab, ready to print on 24-per-sheet sticker paper. */
  async function printLabels(ids: string[]) {
    // Open the tab now, inside the click: browsers block pop-ups opened after an await
    const tab = window.open("", "_blank");
    setPrinting(true);
    try {
      const pdf = await api.getBlob("/staff/copies/labels.pdf", {
        query: ids.length > 0 ? { ids } : { bookId },
      });
      const url = URL.createObjectURL(pdf);
      if (tab) tab.location.href = url;
      else window.location.href = url;
      // Give the new tab time to load it before releasing the memory
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (error) {
      tab?.close();
      toast.error(errorMessage(error));
    } finally {
      setPrinting(false);
    }
  }

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

  const copies = query.data;
  const openCopy = copies.find((c) => c.id === openCopyId);
  const allSelected = copies.length > 0 && selected.length === copies.length;

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap gap-2">
        <Button size="sm" onClick={() => setAdding(true)}>
          <Plus aria-hidden />
          {t("add")}
        </Button>
        {copies.length > 0 && (
          <Button
            size="sm"
            variant="secondary"
            disabled={printing}
            onClick={() => void printLabels(selected)}
          >
            <Printer aria-hidden />
            {printing
              ? t("preparing")
              : selected.length > 0
                ? t("printSelected", { count: selected.length })
                : t("printAll")}
          </Button>
        )}
      </div>

      {copies.length === 0 ? (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("empty")}</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-8">
                <input
                  type="checkbox"
                  className="accent-primary"
                  aria-label={t("selectAll")}
                  checked={allSelected}
                  onChange={(event) =>
                    setSelected(event.target.checked ? copies.map((c) => c.id) : [])
                  }
                />
              </TableHead>
              <TableHead>{t("barcode")}</TableHead>
              <TableHead>{t("status")}</TableHead>
              <TableHead>{t("lendingType")}</TableHead>
              <TableHead>{t("condition")}</TableHead>
              <TableHead>{t("shelf")}</TableHead>
              <TableHead className="text-right">{t("price")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {copies.map((copy) => (
              <TableRow key={copy.id}>
                <TableCell>
                  <input
                    type="checkbox"
                    className="accent-primary"
                    aria-label={t("select", { barcode: copy.barcode })}
                    checked={selected.includes(copy.id)}
                    onChange={(event) =>
                      setSelected((ids) =>
                        event.target.checked
                          ? [...ids, copy.id]
                          : ids.filter((id) => id !== copy.id),
                      )
                    }
                  />
                </TableCell>
                <TableCell className="font-mono">
                  <button
                    type="button"
                    className="underline-offset-4 hover:underline"
                    onClick={() => setOpenCopyId(copy.id)}
                  >
                    {copy.barcode}
                  </button>
                </TableCell>
                <TableCell>
                  <Badge variant={STATUS_VARIANT[copy.status]}>
                    {t(`statuses.${copy.status}`)}
                  </Badge>
                </TableCell>
                <TableCell>{t(`lendingTypes.${copy.lendingType}`)}</TableCell>
                <TableCell>{t(`conditions.${copy.condition}`)}</TableCell>
                <TableCell>{copy.shelfLocation ?? "–"}</TableCell>
                <TableCell className="text-right">
                  {copy.price != null
                    ? format.number(copy.price, { minimumFractionDigits: 2 })
                    : "–"}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={openCopy !== undefined} onOpenChange={(open) => !open && setOpenCopyId(null)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
          {openCopy && (
            // key: a fresh form when another copy is opened or this one changed
            <CopyDetail
              key={`${openCopy.id}-${openCopy.status}`}
              copy={openCopy}
              onChanged={() =>
                void queryClient.invalidateQueries({ queryKey: copiesQueryKey(bookId) })
              }
            />
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={adding} onOpenChange={setAdding}>
        <DialogContent className="sm:max-w-lg">
          {adding && <AddCopiesForm bookId={bookId} onDone={() => setAdding(false)} />}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function AddCopiesForm({ bookId, onDone }: { bookId: string; onDone: () => void }) {
  const t = useTranslations("Copies");
  const tDonations = useTranslations("Donations");
  const v = useTranslations("Auth.validation");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const [donation, setDonation] = useState<EntityChoice | null>(null);

  const optionalNumber = (value: unknown) =>
    value === "" || value === null || value === undefined ? undefined : Number(value);
  const schema = z.object({
    count: z
      .number({ error: v("required") })
      .int()
      .min(1)
      .max(50),
    barcode: z
      .string()
      .trim()
      .refine((value) => value === "" || /^[A-Za-z0-9-]{3,30}$/.test(value), v("required")),
    lendingType: z.enum(LENDING_TYPES),
    condition: z.enum(CONDITIONS),
    shelfLocation: z.string().trim().max(100, v("tooLong")),
    acquiredAt: z.string(),
    acquisitionSource: z.enum(SOURCES),
    price: z.number().min(0).optional(),
    notes: z.string().trim().max(500, v("tooLong")),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      count: 1,
      barcode: "",
      lendingType: "LENDABLE",
      condition: "NEW",
      shelfLocation: "",
      acquiredAt: new Date().toISOString().slice(0, 10),
      acquisitionSource: "PURCHASE",
      notes: "",
    },
  });
  const { errors, isSubmitting } = form.formState;
  const ownBarcode = useWatch({ control: form.control, name: "barcode" }).trim() !== "";
  const selectClass = "h-8 w-full rounded-lg border bg-transparent px-2.5 text-sm";

  const applyFieldErrors = useFieldErrors();
  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    const body: AddCopiesRequest = {
      barcode: values.barcode || undefined,
      count: values.barcode ? 1 : values.count,
      details: {
        lendingType: values.lendingType,
        condition: values.condition,
        shelfLocation: values.shelfLocation || undefined,
        acquiredAt: values.acquiredAt || undefined,
        acquisitionSource: values.acquisitionSource,
        price: values.price,
        notes: values.notes || undefined,
        // Linking a donation also sets the source to DONATION on the server
        donationId: donation?.id,
      },
    };
    try {
      const added = await api.post<Copy[]>(`/staff/books/${bookId}/copies`, body);
      toast.success(
        t("added", { count: added.length, barcodes: added.map((c) => c.barcode).join(", ") }),
      );
      await queryClient.invalidateQueries({ queryKey: copiesQueryKey(bookId) });
      onDone();
    } catch (error) {
      const translate = ({ code }: { code: string }) =>
        t.has(`errors.${code}` as never) ? t(`errors.${code}` as never) : undefined;
      if (!applyFieldErrors(error, form.setError, translate)) {
        setFormError(
          error instanceof ApiError
            ? (translate(error) ?? errorMessage(error))
            : errorMessage(error),
        );
      }
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>{t("addTitle")}</DialogTitle>
      </DialogHeader>
      {formError && <FormAlert>{formError}</FormAlert>}

      <div className="grid gap-4 sm:grid-cols-[8rem_1fr]">
        <FormField label={t("count")} error={errors.count?.message}>
          {(field) => (
            <Input
              type="number"
              min={1}
              max={50}
              disabled={ownBarcode}
              {...field}
              {...form.register("count", { setValueAs: Number })}
            />
          )}
        </FormField>
        <FormField
          label={t("barcodeOptional")}
          hint={t("barcodeHint")}
          error={errors.barcode?.message}
        >
          {(field) => (
            <Input className="font-mono uppercase" {...field} {...form.register("barcode")} />
          )}
        </FormField>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label={t("lendingType")}>
          {(field) => (
            <select className={selectClass} {...field} {...form.register("lendingType")}>
              {LENDING_TYPES.map((value) => (
                <option key={value} value={value}>
                  {t(`lendingTypes.${value}`)}
                </option>
              ))}
            </select>
          )}
        </FormField>
        <FormField label={t("condition")}>
          {(field) => (
            <select className={selectClass} {...field} {...form.register("condition")}>
              {CONDITIONS.map((value) => (
                <option key={value} value={value}>
                  {t(`conditions.${value}`)}
                </option>
              ))}
            </select>
          )}
        </FormField>
        <FormField label={t("shelf")} error={errors.shelfLocation?.message}>
          {(field) => <Input {...field} {...form.register("shelfLocation")} />}
        </FormField>
        <FormField label={t("acquiredAt")}>
          {(field) => <Input type="date" {...field} {...form.register("acquiredAt")} />}
        </FormField>
        <FormField label={t("source")}>
          {(field) => (
            <select className={selectClass} {...field} {...form.register("acquisitionSource")}>
              {SOURCES.map((value) => (
                <option key={value} value={value}>
                  {t(`sources.${value}`)}
                </option>
              ))}
            </select>
          )}
        </FormField>
        <FormField label={t("price")} error={errors.price?.message}>
          {(field) => (
            <Input
              type="number"
              step="0.01"
              min={0}
              inputMode="decimal"
              {...field}
              {...form.register("price", { setValueAs: optionalNumber })}
            />
          )}
        </FormField>
      </div>
      <FormField label={t("notes")} error={errors.notes?.message}>
        {(field) => <Input {...field} {...form.register("notes")} />}
      </FormField>
      <FormField label={tDonations("fromDonation")}>
        {(field) => (
          <EntityCombobox
            {...field}
            value={donation}
            onChange={(choice) => {
              setDonation(choice);
              if (choice) form.setValue("acquisitionSource", "DONATION");
            }}
            search={searchDonations}
            queryKey="donations"
            placeholder={tDonations("fromDonationPlaceholder")}
            allowCreate={false}
          />
        )}
      </FormField>

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
