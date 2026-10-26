"use client";

import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowUp, Plus, ScanBarcode, Trash2 } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { CategoryTreePicker } from "@/components/forms/category-tree-picker";
import { EntityCombobox, type EntityChoice } from "@/components/forms/entity-combobox";
import { FormField } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Link, useRouter } from "@/i18n/navigation";
import { ApiError, api, type PageResponse, type Schema } from "@/lib/api";
import { applyServerErrors } from "@/lib/forms";

type Book = Schema<"BookResponse">;
type BookRequest = Schema<"BookRequest">;
type IsbnLookup = Schema<"IsbnLookupResponse">;
type CategoryNode = Schema<"CategoryTreeResponse">;
type Role = NonNullable<Schema<"ContributorRequest">["role"]>;

const ROLES: Role[] = ["AUTHOR", "EDITOR", "TRANSLATOR", "ILLUSTRATOR"];
const FORMATS = ["BOOK", "MAGAZINE", "NEWSPAPER", "DVD", "AUDIOBOOK"] as const;
const MAX_CATEGORIES = 10;

type Contributor = { key: number; person: EntityChoice | null; role: Role };

// Author picker: typo-tolerant suggestions (step 154)
const searchAuthors = (text: string) =>
  api.get<Schema<"AuthorResponse">[]>("/staff/authors/suggest", { query: { q: text } });
const searchPublishers = async (text: string) =>
  (
    await api.get<PageResponse<Schema<"PublisherResponse">>>("/staff/publishers", {
      query: { q: text, size: 10 },
    })
  ).content;

/**
 * Add a title to the catalog (checklist step 173): "Lookup ISBN" fills what Open Library knows,
 * staff check it, pick or create authors and publisher (step 174), and save. New authors and
 * publishers are created only when the book is saved. Copies are added on the book's page.
 */
export function BookForm() {
  const t = useTranslations("BookForm");
  const tCatalog = useTranslations("StaffCatalog");
  const v = useTranslations("Auth.validation");
  const locale = useLocale();
  const errorMessage = useAuthErrorMessage();
  const router = useRouter();
  const queryClient = useQueryClient();

  const [contributors, setContributors] = useState<Contributor[]>([
    { key: 0, person: null, role: "AUTHOR" },
  ]);
  const [nextKey, setNextKey] = useState(1);
  const [publisher, setPublisher] = useState<EntityChoice | null>(null);
  const [categoryIds, setCategoryIds] = useState<string[]>([]);
  const [lookup, setLookup] = useState<IsbnLookup | null>(null);
  const [lookupMessage, setLookupMessage] = useState<string | null>(null);
  const [lookingUp, setLookingUp] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: () => api.get<CategoryNode[]>("/categories"),
    staleTime: 5 * 60_000,
  });

  const optionalNumber = (value: unknown) =>
    value === "" || value === null || value === undefined ? undefined : Number(value);
  const schema = z.object({
    isbn: z.string().trim().max(20),
    title: z.string().trim().min(1, v("required")).max(300, v("tooLong")),
    subtitle: z.string().trim().max(300, v("tooLong")),
    edition: z.string().trim().max(50, v("tooLong")),
    publishYear: z.number().int().min(1000).max(2100).optional(),
    pages: z.number().int().min(1).max(100000).optional(),
    language: z
      .string()
      .trim()
      .regex(/^[a-zA-Z]{2,3}$/, v("required")),
    format: z.enum(FORMATS),
    callNumber: z.string().trim().max(50, v("tooLong")),
    description: z.string().trim().max(10000, v("tooLong")),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      isbn: "",
      title: "",
      subtitle: "",
      edition: "",
      language: locale,
      format: "BOOK",
      callNumber: "",
      description: "",
    },
  });
  const { errors, isSubmitting } = form.formState;

  /** Fills the form from Open Library; existing authors/publisher are matched by exact name. */
  async function lookupIsbn() {
    const isbn = form.getValues("isbn").trim();
    if (!isbn) return;
    setLookingUp(true);
    setLookupMessage(null);
    setLookup(null);
    try {
      const found = await api.get<IsbnLookup>(`/staff/isbn/${encodeURIComponent(isbn)}`);
      setLookup(found);
      form.setValue("isbn", found.isbn13);
      if (found.existingBookId || !found.found) {
        setLookupMessage(found.existingBookId ? null : t("lookupNotFound"));
        return;
      }
      form.setValue("title", found.title ?? "");
      form.setValue("subtitle", found.subtitle ?? "");
      if (found.publishYear) form.setValue("publishYear", found.publishYear);
      if (found.pages) form.setValue("pages", found.pages);
      const people = await Promise.all(
        found.authors.map(async (name): Promise<EntityChoice> => {
          const matches = await searchAuthors(name);
          return matches.find((m) => m.name.toLowerCase() === name.toLowerCase()) ?? { name };
        }),
      );
      if (people.length > 0) {
        setContributors(people.map((person, i) => ({ key: i, person, role: "AUTHOR" })));
        setNextKey(people.length);
      }
      if (found.publisher) {
        const name = found.publisher;
        const matches = await searchPublishers(name);
        setPublisher(matches.find((m) => m.name.toLowerCase() === name.toLowerCase()) ?? { name });
      }
      setLookupMessage(t("lookupFound"));
    } catch (error) {
      if (error instanceof ApiError && error.code === "ISBN_INVALID") {
        form.setError("isbn", { message: t("errors.ISBN_INVALID") });
      } else {
        setLookupMessage(
          error instanceof ApiError && error.code === "ISBN_LOOKUP_UNAVAILABLE"
            ? t("lookupUnavailable")
            : errorMessage(error),
        );
      }
    } finally {
      setLookingUp(false);
    }
  }

  /** New authors / publisher are created now, just before the book itself. */
  async function ensureId(choice: EntityChoice, path: string) {
    if (choice.id) return choice.id;
    const created = await api.post<{ id: string }>(path, { name: choice.name });
    return created.id;
  }

  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    try {
      const chosen = contributors.filter((c) => c.person);
      const authorIds = await Promise.all(chosen.map((c) => ensureId(c.person!, "/staff/authors")));
      const body: BookRequest = {
        title: values.title,
        subtitle: values.subtitle || undefined,
        isbn: values.isbn || undefined,
        edition: values.edition || undefined,
        publishYear: values.publishYear,
        pages: values.pages,
        language: values.language.toLowerCase(),
        description: values.description || undefined,
        format: values.format,
        callNumber: values.callNumber || undefined,
        publisherId: publisher ? await ensureId(publisher, "/staff/publishers") : undefined,
        contributors: chosen.map((c, i) => ({ authorId: authorIds[i], role: c.role })),
        categoryIds,
      };
      const book = await api.post<Book>("/staff/books", body);
      toast.success(t("saved", { title: book.title }));
      await queryClient.invalidateQueries({ queryKey: ["staff", "catalog"] });
      router.push(`/staff/catalog?q=${encodeURIComponent(book.isbn13 ?? book.title)}`);
    } catch (error) {
      const translate = ({ code }: { code: string }) =>
        t.has(`errors.${code}` as never) ? t(`errors.${code}` as never) : undefined;
      if (error instanceof ApiError && error.code === "BOOK_ISBN_EXISTS") {
        form.setError("isbn", { message: t("errors.BOOK_ISBN_EXISTS") }, { shouldFocus: true });
      } else if (!applyServerErrors(error, form.setError, translate)) {
        setFormError(
          translate({ code: error instanceof ApiError ? error.code : "" }) ?? errorMessage(error),
        );
      }
    }
  });

  const selectClass = "h-8 rounded-lg border bg-transparent px-2.5 text-sm";

  return (
    <form onSubmit={onSubmit} noValidate className="grid max-w-3xl gap-6">
      {formError && <FormAlert>{formError}</FormAlert>}

      {/* ISBN + lookup */}
      <section className="grid gap-3 rounded-lg border p-4">
        <FormField label={t("isbn")} hint={t("isbnHint")} error={errors.isbn?.message}>
          {(field) => (
            <div className="flex gap-2">
              <Input
                inputMode="numeric"
                autoFocus
                {...field}
                {...form.register("isbn")}
                onKeyDown={(event) => {
                  // A scanner "types" the ISBN and presses Enter: look it up instead of saving
                  if (event.key === "Enter") {
                    event.preventDefault();
                    void lookupIsbn();
                  }
                }}
              />
              <Button
                type="button"
                variant="secondary"
                onClick={() => void lookupIsbn()}
                disabled={lookingUp}
              >
                <ScanBarcode aria-hidden />
                {lookingUp ? t("lookingUp") : t("lookup")}
              </Button>
            </div>
          )}
        </FormField>
        {lookup?.existingBookId && (
          <FormAlert>
            {t("alreadyInCatalog")}{" "}
            <Link
              href={`/staff/catalog/${lookup.existingBookId}`}
              className="font-medium underline"
            >
              {lookup.existingBookTitle}
            </Link>{" "}
            ({t("addCopyInstead")})
          </FormAlert>
        )}
        {lookupMessage && (
          <p role="status" className="text-sm text-muted-foreground">
            {lookupMessage}
          </p>
        )}
        {lookup?.found && lookup.coverImageUrl && (
          <figure className="flex items-center gap-3 text-xs text-muted-foreground">
            {/* An outside preview only; our own cover is uploaded on the book's page */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={lookup.coverImageUrl} alt="" className="h-24 rounded border object-cover" />
            <figcaption>{t("openLibraryCover")}</figcaption>
          </figure>
        )}
      </section>

      <section className="grid gap-4">
        <FormField label={t("bookTitle")} error={errors.title?.message} required>
          {(field) => <Input {...field} {...form.register("title")} />}
        </FormField>
        <FormField label={t("subtitle")} error={errors.subtitle?.message}>
          {(field) => <Input {...field} {...form.register("subtitle")} />}
        </FormField>

        {/* Contributors, in cover order */}
        <fieldset className="grid gap-2">
          <legend className="text-sm font-medium">{t("contributors")}</legend>
          <p className="text-sm text-muted-foreground">{t("contributorsHint")}</p>
          {contributors.map((contributor, index) => (
            <div key={contributor.key} className="flex flex-wrap items-center gap-2">
              <div className="min-w-56 flex-1">
                <EntityCombobox
                  id={`contributor-${contributor.key}`}
                  aria-label={`${t("author")} ${index + 1}`}
                  value={contributor.person}
                  onChange={(person) =>
                    setContributors((list) =>
                      list.map((c) => (c.key === contributor.key ? { ...c, person } : c)),
                    )
                  }
                  search={searchAuthors}
                  queryKey="authors"
                  placeholder={t("authorPlaceholder")}
                />
              </div>
              <select
                aria-label={`${t("role")} ${index + 1}`}
                className={selectClass}
                value={contributor.role}
                onChange={(event) =>
                  setContributors((list) =>
                    list.map((c) =>
                      c.key === contributor.key ? { ...c, role: event.target.value as Role } : c,
                    ),
                  )
                }
              >
                {ROLES.map((role) => (
                  <option key={role} value={role}>
                    {t(`roles.${role}`)}
                  </option>
                ))}
              </select>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={t("moveUp")}
                disabled={index === 0}
                onClick={() =>
                  setContributors((list) => {
                    const copy = [...list];
                    [copy[index - 1], copy[index]] = [copy[index], copy[index - 1]];
                    return copy;
                  })
                }
              >
                <ArrowUp />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={t("removeContributor")}
                onClick={() =>
                  setContributors((list) => list.filter((c) => c.key !== contributor.key))
                }
              >
                <Trash2 />
              </Button>
            </div>
          ))}
          <Button
            type="button"
            variant="secondary"
            size="sm"
            className="justify-self-start"
            onClick={() => {
              setContributors((list) => [...list, { key: nextKey, person: null, role: "AUTHOR" }]);
              setNextKey((key) => key + 1);
            }}
          >
            <Plus aria-hidden />
            {t("addContributor")}
          </Button>
        </fieldset>

        <FormField label={t("publisher")}>
          {(field) => (
            <EntityCombobox
              {...field}
              value={publisher}
              onChange={setPublisher}
              search={searchPublishers}
              queryKey="publishers"
              placeholder={t("publisherPlaceholder")}
            />
          )}
        </FormField>

        <div className="grid gap-4 sm:grid-cols-3">
          <FormField label={t("edition")} error={errors.edition?.message}>
            {(field) => <Input {...field} {...form.register("edition")} />}
          </FormField>
          <FormField label={t("publishYear")} error={errors.publishYear?.message}>
            {(field) => (
              <Input
                type="number"
                inputMode="numeric"
                {...field}
                {...form.register("publishYear", { setValueAs: optionalNumber })}
              />
            )}
          </FormField>
          <FormField label={t("pages")} error={errors.pages?.message}>
            {(field) => (
              <Input
                type="number"
                inputMode="numeric"
                {...field}
                {...form.register("pages", { setValueAs: optionalNumber })}
              />
            )}
          </FormField>
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <FormField label={t("language")} error={errors.language?.message} required>
            {(field) => (
              <Input
                list="book-languages"
                maxLength={3}
                {...field}
                {...form.register("language")}
              />
            )}
          </FormField>
          <datalist id="book-languages">
            <option value="si">සිංහල</option>
            <option value="ta">தமிழ்</option>
            <option value="en">English</option>
          </datalist>
          <FormField label={t("format")}>
            {(field) => (
              <select className={selectClass} {...field} {...form.register("format")}>
                {FORMATS.map((format) => (
                  <option key={format} value={format}>
                    {tCatalog(`formats.${format}`)}
                  </option>
                ))}
              </select>
            )}
          </FormField>
          <FormField
            label={t("callNumber")}
            hint={t("callNumberHint")}
            error={errors.callNumber?.message}
          >
            {(field) => <Input className="font-mono" {...field} {...form.register("callNumber")} />}
          </FormField>
        </div>

        <FormField label={t("description")} error={errors.description?.message}>
          {(field) => <Textarea rows={4} {...field} {...form.register("description")} />}
        </FormField>

        <div className="grid gap-1">
          <span id="book-categories-label" className="text-sm font-medium">
            {t("categories")}
          </span>
          <CategoryTreePicker
            tree={categories.data ?? []}
            value={categoryIds}
            onChange={setCategoryIds}
            max={MAX_CATEGORIES}
            labelledBy="book-categories-label"
          />
        </div>
      </section>

      <Button type="submit" className="justify-self-start" disabled={isSubmitting}>
        {isSubmitting ? t("saving") : t("save")}
      </Button>
    </form>
  );
}
