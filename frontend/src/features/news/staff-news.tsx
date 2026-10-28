"use client";

import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Pin, Plus } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
import { Pager } from "@/features/staff/pager";
import { Link } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";

export type StaffNewsPost = Schema<"NewsPostResponse">;
type NewsRow = Schema<"NewsSummary">;

export const STAFF_NEWS_KEY = ["staff", "news"] as const;

/** Staff news list (checklist step 347): drafts too, most recently changed first. */
export function StaffNews() {
  const t = useTranslations("StaffNews");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: [...STAFF_NEWS_KEY, page],
    queryFn: () => api.get<PageResponse<NewsRow>>("/staff/news", { query: { page, size: 20 } }),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="grid gap-4">
      <div>
        <Button asChild>
          <Link href="/staff/news/new">
            <Plus aria-hidden />
            {t("new")}
          </Link>
        </Button>
      </div>

      {query.isPending && <HexLoader label={tCommon("loading")} />}
      {query.isError && <FormAlert>{errorMessage(query.error)}</FormAlert>}
      {query.data && query.data.totalElements === 0 && (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("empty")}</p>
      )}
      {query.data && query.data.totalElements > 0 && (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("post")}</TableHead>
                <TableHead>{t("status")}</TableHead>
                <TableHead>{t("changed")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {query.data.content.map((post) => (
                <TableRow key={post.id}>
                  <TableCell>
                    <span className="flex items-center gap-1.5">
                      {post.pinned && (
                        <Pin aria-label={t("pinned")} className="size-3.5 shrink-0 text-primary" />
                      )}
                      <Link
                        href={`/staff/news/${post.id}`}
                        className="font-medium underline-offset-4 hover:underline"
                      >
                        {post.title}
                      </Link>
                    </span>
                  </TableCell>
                  <TableCell>
                    <NewsStatusBadge status={post.status} />
                  </TableCell>
                  <TableCell className="text-sm whitespace-nowrap">
                    {format.dateTime(new Date(post.updatedAt), {
                      dateStyle: "medium",
                      timeStyle: "short",
                    })}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pager page={page} totalPages={query.data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}

export function NewsStatusBadge({ status }: { status: StaffNewsPost["status"] }) {
  const t = useTranslations("StaffNews.statuses");
  return status === "PUBLISHED" ? (
    <Badge variant="available">{t("PUBLISHED")}</Badge>
  ) : (
    <Badge variant="outline">{t("DRAFT")}</Badge>
  );
}
