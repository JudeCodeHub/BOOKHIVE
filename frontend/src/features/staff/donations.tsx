"use client";

import { BackLink } from "@/components/layout/back-link";
import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Search } from "lucide-react";
import { useParams } from "next/navigation";
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
  DialogDescription,
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
import { Textarea } from "@/components/ui/textarea";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage, useFieldErrors } from "@/features/auth/errors";
import { Pager } from "@/features/staff/pager";
import { Link } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";

type Donation = Schema<"DonationResponse">;
type DonationDetail = Schema<"DonationDetailResponse">;

const DONATIONS_KEY = ["staff", "donations"] as const;

/** Donations log (checklist step 181): newest first, search by donor, record new ones. */
export function DonationsList() {
  const t = useTranslations("Donations");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const [text, setText] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [recording, setRecording] = useState(false);

  const query = useQuery({
    queryKey: [...DONATIONS_KEY, q, page],
    queryFn: () =>
      api.get<PageResponse<Donation>>("/staff/donations", { query: { q, page, size: 20 } }),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <Button onClick={() => setRecording(true)}>
          <Plus aria-hidden />
          {t("record")}
        </Button>
        <form
          role="search"
          className="flex gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            setPage(0);
            setQ(text.trim());
          }}
        >
          <label htmlFor="donor-search" className="sr-only">
            {t("searchLabel")}
          </label>
          <Input
            id="donor-search"
            type="search"
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder={t("searchPlaceholder")}
          />
          <Button type="submit" variant="secondary" size="icon" aria-label={t("searchLabel")}>
            <Search />
          </Button>
        </form>
      </div>

      {query.isPending && <HexLoader label={tCommon("loading")} />}
      {query.isError && <FormAlert>{errorMessage(query.error)}</FormAlert>}
      {query.data && query.data.totalElements === 0 && (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">
          {q ? t("noMatch", { q }) : t("empty")}
        </p>
      )}
      {query.data && query.data.totalElements > 0 && (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("donor")}</TableHead>
                <TableHead>{t("contact")}</TableHead>
                <TableHead>{t("received")}</TableHead>
                <TableHead className="text-right">{t("copies")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {query.data.content.map((donation) => (
                <TableRow key={donation.id}>
                  <TableCell>
                    <Link
                      href={`/staff/donations/${donation.id}`}
                      className="font-medium underline-offset-4 hover:underline"
                    >
                      {donation.donorName}
                    </Link>
                  </TableCell>
                  <TableCell className="text-sm">{donation.donorContact ?? "–"}</TableCell>
                  <TableCell>
                    {format.dateTime(new Date(donation.receivedAt), { dateStyle: "medium" })}
                  </TableCell>
                  <TableCell className="text-right">{donation.copyCount}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pager page={page} totalPages={query.data.totalPages} onChange={setPage} />
        </>
      )}

      <Dialog open={recording} onOpenChange={setRecording}>
        <DialogContent>
          {recording && <DonationForm onDone={() => setRecording(false)} />}
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** Record a new donation, or edit one. */
function DonationForm({ donation, onDone }: { donation?: Donation; onDone: () => void }) {
  const t = useTranslations("Donations");
  const v = useTranslations("Auth.validation");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);

  const schema = z.object({
    donorName: z.string().trim().min(1, v("required")).max(200, v("tooLong")),
    donorContact: z.string().trim().max(200, v("tooLong")),
    receivedAt: z.string().min(1, v("required")),
    notes: z.string().trim().max(1000, v("tooLong")),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      donorName: donation?.donorName ?? "",
      donorContact: donation?.donorContact ?? "",
      receivedAt: donation?.receivedAt ?? new Date().toISOString().slice(0, 10),
      notes: donation?.notes ?? "",
    },
  });
  const { errors, isSubmitting } = form.formState;

  const applyFieldErrors = useFieldErrors();
  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    const body = {
      ...values,
      donorContact: values.donorContact || undefined,
      notes: values.notes || undefined,
    };
    try {
      if (donation) await api.put(`/staff/donations/${donation.id}`, body);
      else await api.post("/staff/donations", body);
      toast.success(t("saved"));
      await queryClient.invalidateQueries({ queryKey: DONATIONS_KEY });
      onDone();
    } catch (error) {
      if (!applyFieldErrors(error, form.setError)) setFormError(errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>{donation ? t("formTitleEdit") : t("formTitleNew")}</DialogTitle>
      </DialogHeader>
      {formError && <FormAlert>{formError}</FormAlert>}
      <FormField label={t("donorName")} error={errors.donorName?.message} required>
        {(field) => <Input {...field} {...form.register("donorName")} />}
      </FormField>
      <FormField
        label={t("donorContact")}
        hint={t("donorContactHint")}
        error={errors.donorContact?.message}
      >
        {(field) => <Input {...field} {...form.register("donorContact")} />}
      </FormField>
      <FormField label={t("receivedAt")} error={errors.receivedAt?.message} required>
        {(field) => (
          <Input
            type="date"
            max={new Date().toISOString().slice(0, 10)}
            {...field}
            {...form.register("receivedAt")}
          />
        )}
      </FormField>
      <FormField label={t("notes")} error={errors.notes?.message}>
        {(field) => <Textarea rows={3} {...field} {...form.register("notes")} />}
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

/** One donation and the copies that came from it. */
export function DonationPage() {
  const t = useTranslations("Donations");
  const tCopies = useTranslations("Copies");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const { id } = useParams<{ id: string }>();
  const [editing, setEditing] = useState(false);

  const query = useQuery({
    queryKey: [...DONATIONS_KEY, "detail", id],
    queryFn: () => api.get<DonationDetail>(`/staff/donations/${id}`),
  });

  if (query.isPending) return <HexLoader label={tCommon("loading")} />;
  if (query.isError) return <FormAlert>{errorMessage(query.error)}</FormAlert>;

  const { donation, copies } = query.data;
  return (
    <div className="grid gap-4">
      <BackLink href="/staff/donations">{t("back")}</BackLink>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-xl font-semibold">{donation.donorName}</h1>
        <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
          <Pencil aria-hidden />
          {t("edit")}
        </Button>
      </div>
      <dl className="grid max-w-xl grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
        <dt className="text-muted-foreground">{t("received")}</dt>
        <dd>{format.dateTime(new Date(donation.receivedAt), { dateStyle: "long" })}</dd>
        <dt className="text-muted-foreground">{t("contact")}</dt>
        <dd>{donation.donorContact ?? "–"}</dd>
        {donation.notes && (
          <>
            <dt className="text-muted-foreground">{t("notes")}</dt>
            <dd className="whitespace-pre-line">{donation.notes}</dd>
          </>
        )}
      </dl>

      <h2 className="font-medium">{t("donatedCopies")}</h2>
      {copies.length === 0 ? (
        <p className="rounded-lg border p-6 text-sm text-muted-foreground">{t("noCopies")}</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("barcode")}</TableHead>
              <TableHead>{t("book")}</TableHead>
              <TableHead>{t("status")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {copies.map((copy) => (
              <TableRow key={copy.copyId}>
                <TableCell className="font-mono">{copy.barcode}</TableCell>
                <TableCell>
                  <Link
                    href={`/staff/catalog/${copy.bookId}?tab=copies`}
                    className="underline-offset-4 hover:underline"
                  >
                    {copy.title}
                  </Link>
                </TableCell>
                <TableCell>
                  <Badge variant="outline">{tCopies(`statuses.${copy.status}`)}</Badge>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={editing} onOpenChange={setEditing}>
        <DialogContent>
          <DialogDescription className="sr-only">{t("formTitleEdit")}</DialogDescription>
          {editing && <DonationForm donation={donation} onDone={() => setEditing(false)} />}
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** Donations for the "From donation" picker when adding copies. */
export async function searchDonations(text: string) {
  const page = await api.get<PageResponse<Donation>>("/staff/donations", {
    query: { q: text, size: 10 },
  });
  return page.content.map((d) => ({ id: d.id, name: `${d.donorName} · ${d.receivedAt}` }));
}
