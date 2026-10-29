"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { BookCover } from "@/features/catalog/book-cover";
import { Pager } from "@/features/staff/pager";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type PageResponse, type Schema } from "@/lib/api";

type Item = Schema<"WishlistItemSummary">;

const PAGE_SIZE = 24;

/**
 * "My wishlist" (COMM-3, checklist step 350): saved books, newest first, with whether a copy
 * is on the shelf right now, so members know when to come in.
 */
export function MyWishlist() {
  const t = useTranslations("Wishlist");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  const list = useQuery({
    queryKey: ["me", "wishlist", "list", page],
    queryFn: () =>
      api.get<PageResponse<Item>>("/me/wishlist", { query: { page, size: PAGE_SIZE } }),
    placeholderData: keepPreviousData,
  });

  const remove = useMutation({
    mutationFn: (item: Item) => api.delete(`/me/wishlist/${item.bookId}`),
    onSuccess: (_, item) => {
      toast.success(item.title ? t("removedTitle", { title: item.title }) : t("removed"));
      queryClient.setQueryData(["me", "wishlist", "status", item.bookId], { saved: false });
      void queryClient.invalidateQueries({ queryKey: ["me", "wishlist", "list"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  if (list.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (list.isError) {
    if (list.error instanceof ApiError && list.error.code === "MEMBER_NOT_FOUND") {
      return (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">
          {t.rich("notMember", {
            link: (chunks) => (
              <Link href="/account/membership" className="font-medium text-link underline">
                {chunks}
              </Link>
            ),
          })}
        </p>
      );
    }
    return <FormAlert>{errorMessage(list.error)}</FormAlert>;
  }
  if (list.data.totalElements === 0) {
    return (
      <p className="rounded-lg border p-6 text-center text-muted-foreground">
        {t.rich("empty", {
          link: (chunks) => (
            <Link href="/search" className="font-medium text-link underline">
              {chunks}
            </Link>
          ),
        })}
      </p>
    );
  }

  return (
    <div className="grid gap-4">
      <p className="text-sm text-muted-foreground">
        {t("count", { count: list.data.totalElements })}
      </p>
      <ul className="grid gap-3 sm:grid-cols-2">
        {list.data.content.map((item) => (
          <li key={item.bookId} className="flex gap-3 rounded-lg border bg-card p-3">
            <BookCover
              bookId={item.bookId}
              version={item.coverVersion}
              alt=""
              sizes="64px"
              className="w-16 shrink-0"
            />
            <div className="grid min-w-0 flex-1 content-start gap-1.5">
              {item.title ? (
                <Link
                  href={`/books/${item.bookId}`}
                  className="line-clamp-2 font-heading font-semibold underline-offset-4 hover:underline"
                >
                  {item.title}
                </Link>
              ) : (
                <p className="text-muted-foreground italic">{t("gone")}</p>
              )}
              <div>
                <Availability item={item} />
              </div>
              <p className="text-xs text-muted-foreground">
                {t("addedOn", {
                  date: format.dateTime(new Date(item.addedAt), { dateStyle: "medium" }),
                })}
              </p>
              <Button
                size="sm"
                variant="ghost"
                className="justify-self-start"
                disabled={remove.isPending && remove.variables?.bookId === item.bookId}
                onClick={() => remove.mutate(item)}
              >
                <Trash2 aria-hidden />
                {t("remove")}
              </Button>
            </div>
          </li>
        ))}
      </ul>
      <Pager page={page} totalPages={list.data.totalPages} onChange={setPage} />
    </div>
  );
}

function Availability({ item }: { item: Item }) {
  const t = useTranslations("Wishlist");
  if (!item.title) return null;
  if (item.lendableCopies === 0) return <Badge variant="reference">{t("referenceOnly")}</Badge>;
  if (item.availableNow > 0) {
    return <Badge variant="available">{t("onShelf", { count: item.availableNow })}</Badge>;
  }
  return <Badge variant="on-loan">{t("allOut")}</Badge>;
}
