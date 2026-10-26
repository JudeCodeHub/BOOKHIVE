"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Users } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { EntityCombobox, type EntityChoice } from "@/components/forms/entity-combobox";
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
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Pager } from "@/features/staff/pager";
import { Link } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";

type Summary = Schema<"StaffSuggestionSummary">;
type Status = Summary["suggestion"]["status"];

const STATUSES: Status[] = ["SUBMITTED", "ORDERED", "ACQUIRED", "DECLINED"];
const QUERY_KEY = ["staff", "suggestions"] as const;

/** Books to pick from when marking a suggestion bought: title search over the catalog. */
const searchBooks = async (text: string) =>
  (
    await api.get<PageResponse<Schema<"BookSummary">>>("/books", { query: { q: text, size: 10 } })
  ).content.map((book) => ({
    id: book.id,
    name: book.contributors.length > 0 ? `${book.title} · ${book.contributors[0]}` : book.title,
  }));

/** Members' purchase suggestions for staff (checklist step 182). */
export function SuggestionsQueue() {
  const t = useTranslations("SuggestionsQueue");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<Status>("SUBMITTED");
  const [page, setPage] = useState(0);
  // The suggestion being answered and the answer chosen (needs a note or a book)
  const [deciding, setDeciding] = useState<{ item: Summary; to: Status } | null>(null);

  const query = useQuery({
    queryKey: [...QUERY_KEY, status, page],
    queryFn: () =>
      api.get<PageResponse<Summary>>("/staff/suggestions", { query: { status, page, size: 20 } }),
    placeholderData: keepPreviousData,
  });

  const decide = useMutation({
    mutationFn: ({
      id,
      to,
      note,
      bookId,
    }: {
      id: string;
      to: Status;
      note?: string;
      bookId?: string;
    }) => api.patch(`/staff/suggestions/${id}`, { status: to, staffNote: note, bookId }),
    onSuccess: async () => {
      toast.success(t("saved"));
      setDeciding(null);
      await queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="grid gap-4">
      <Tabs
        value={status}
        onValueChange={(value) => {
          setStatus(value as Status);
          setPage(0);
        }}
      >
        <TabsList aria-label={t("tabs")}>
          {STATUSES.map((value) => (
            <TabsTrigger key={value} value={value}>
              {t(`statuses.${value}`)}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {query.isPending && <HexLoader label={tCommon("loading")} />}
      {query.isError && <FormAlert>{errorMessage(query.error)}</FormAlert>}
      {query.data?.totalElements === 0 && (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("empty")}</p>
      )}

      <ul className="grid gap-3">
        {query.data?.content.map((item) => {
          const s = item.suggestion;
          return (
            <li key={s.id} className="grid gap-2 rounded-lg border p-4">
              <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <h2 className="font-semibold">{s.title}</h2>
                {s.author && <span className="text-sm">{s.author}</span>}
                {s.isbn13 && (
                  <span className="font-mono text-xs text-muted-foreground">{s.isbn13}</span>
                )}
                {item.sameIsbnCount > 1 && (
                  <Badge variant="on-loan">
                    <Users aria-hidden />
                    {t("sameIsbn", { count: item.sameIsbnCount })}
                  </Badge>
                )}
              </div>
              <p className="text-xs text-muted-foreground">
                {t("by", {
                  name: item.suggestedByName ?? "–",
                  date: format.dateTime(new Date(s.createdAt), { dateStyle: "medium" }),
                })}
                {item.suggestedByEmail && ` · ${item.suggestedByEmail}`}
              </p>
              {s.reason && <p className="text-sm">{t("reason", { reason: s.reason })}</p>}
              {s.staffNote && (
                <p className="text-sm text-muted-foreground">
                  {t("staffNote", { note: s.staffNote })}
                </p>
              )}
              {s.bookId && (
                <p className="text-sm">
                  {t("linkedBook")}{" "}
                  <Link href={`/staff/catalog/${s.bookId}`} className="underline">
                    {s.title}
                  </Link>
                </p>
              )}
              <div className="flex flex-wrap gap-2">
                {s.status === "SUBMITTED" && (
                  <Button
                    size="sm"
                    variant="secondary"
                    disabled={decide.isPending}
                    onClick={() => decide.mutate({ id: s.id, to: "ORDERED" })}
                  >
                    {t("markOrdered")}
                  </Button>
                )}
                {(s.status === "SUBMITTED" || s.status === "ORDERED") && (
                  <>
                    <Button size="sm" onClick={() => setDeciding({ item, to: "ACQUIRED" })}>
                      {t("markAcquired")}
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setDeciding({ item, to: "DECLINED" })}
                    >
                      {t("decline")}
                    </Button>
                  </>
                )}
                {s.status !== "SUBMITTED" && (
                  <Button
                    size="sm"
                    variant="ghost"
                    disabled={decide.isPending}
                    onClick={() => decide.mutate({ id: s.id, to: "SUBMITTED" })}
                  >
                    {t("reopen")}
                  </Button>
                )}
              </div>
            </li>
          );
        })}
      </ul>
      {query.data && <Pager page={page} totalPages={query.data.totalPages} onChange={setPage} />}

      <Dialog open={deciding !== null} onOpenChange={(open) => !open && setDeciding(null)}>
        <DialogContent>
          {deciding && (
            <DecisionForm
              key={deciding.item.suggestion.id + deciding.to}
              item={deciding.item}
              to={deciding.to}
              busy={decide.isPending}
              onCancel={() => setDeciding(null)}
              onSave={(note, bookId) =>
                decide.mutate({ id: deciding.item.suggestion.id, to: deciding.to, note, bookId })
              }
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** Note for the member; for "bought" also the catalog book it became. */
function DecisionForm({
  item,
  to,
  busy,
  onCancel,
  onSave,
}: {
  item: Summary;
  to: Status;
  busy: boolean;
  onCancel: () => void;
  onSave: (note: string | undefined, bookId: string | undefined) => void;
}) {
  const t = useTranslations("SuggestionsQueue");
  const v = useTranslations("Auth.validation");
  const [note, setNote] = useState(item.suggestion.staffNote ?? "");
  const [book, setBook] = useState<EntityChoice | null>(null);
  const [error, setError] = useState<{ note?: string; book?: string }>({});

  function save() {
    // A decline must say why; a purchase must point to the book
    if (to === "DECLINED" && !note.trim()) return setError({ note: t("noteRequired") });
    if (to === "ACQUIRED" && !book?.id) return setError({ book: v("required") });
    onSave(note.trim() || undefined, book?.id);
  }

  return (
    <div className="grid gap-4">
      <DialogHeader>
        <DialogTitle>
          {t("decideTitle", { status: t(`statuses.${to}`), title: item.suggestion.title })}
        </DialogTitle>
        <DialogDescription>{t("noteHint")}</DialogDescription>
      </DialogHeader>
      {to === "ACQUIRED" && (
        <FormField label={t("book")} error={error.book} required>
          {(field) => (
            <EntityCombobox
              {...field}
              value={book}
              onChange={setBook}
              search={searchBooks}
              queryKey="books"
              placeholder={t("bookPlaceholder")}
              allowCreate={false}
            />
          )}
        </FormField>
      )}
      <FormField label={t("note")} error={error.note} required={to === "DECLINED"}>
        {(field) => (
          <Textarea
            {...field}
            rows={3}
            maxLength={1000}
            value={note}
            onChange={(event) => {
              setNote(event.target.value);
              setError({});
            }}
          />
        )}
      </FormField>
      <DialogFooter>
        <Button type="button" variant="secondary" onClick={onCancel}>
          {t("cancel")}
        </Button>
        <Button type="button" disabled={busy} onClick={save}>
          {busy ? t("saving") : t("save")}
        </Button>
      </DialogFooter>
    </div>
  );
}
