"use client";

import { BackLink } from "@/components/layout/back-link";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams, useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { BookForm } from "@/features/staff/book-form";
import { CopiesTab } from "@/features/staff/copies-tab";
import { CoverUpload } from "@/features/staff/cover-upload";
import { usePathname, useRouter } from "@/i18n/navigation";
import { api, type Schema } from "@/lib/api";

type Book = Schema<"BookResponse">;

/**
 * One book for staff (checklist steps 177-178): cover on the side, then tabs for the details
 * (the same form as "Add book", filled in) and the copies. ?tab=copies opens the copies tab,
 * which is where "Add book" sends you after saving.
 */
export function BookPage() {
  const t = useTranslations("StaffBook");
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();
  const tab = useSearchParams().get("tab") === "copies" ? "copies" : "details";
  const { id } = useParams<{ id: string }>();
  const queryKey = ["staff", "book", id];

  const query = useQuery({
    queryKey,
    queryFn: () => api.get<Book>(`/books/${id}`),
  });
  const update = (book: Book) => queryClient.setQueryData(queryKey, book);

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

  const book = query.data;
  return (
    <div className="grid gap-4">
      <BackLink href="/staff/catalog">{t("back")}</BackLink>
      <div>
        <h1 className="text-xl font-semibold">{book.title}</h1>
        <p className="text-sm text-muted-foreground">
          {[
            book.contributors.map((c) => c.name).join(", "),
            book.isbn13 ? t("isbn", { isbn: book.isbn13 }) : t("noIsbn"),
          ]
            .filter(Boolean)
            .join(" · ")}
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[12rem_1fr]">
        <CoverUpload book={book} onChange={update} />

        <Tabs
          value={tab}
          // The tab is in the URL, so a reload or a shared link opens the same one
          onValueChange={(value) =>
            router.replace(value === "copies" ? `${pathname}?tab=copies` : pathname)
          }
        >
          <TabsList aria-label={t("tabs")}>
            <TabsTrigger value="details">{t("details")}</TabsTrigger>
            <TabsTrigger value="copies">{t("copies")}</TabsTrigger>
          </TabsList>
          <TabsContent value="details" className="pt-2">
            {/* key: start the form fresh when the saved book changes (e.g. after saving) */}
            <BookForm key={book.id} book={book} onSaved={update} />
          </TabsContent>
          <TabsContent value="copies" className="pt-2">
            <CopiesTab bookId={book.id} />
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
