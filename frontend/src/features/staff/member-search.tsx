"use client";

import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useFormatter, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
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
import { MemberStatusBadge } from "@/features/staff/member-status-badge";
import { Pager } from "@/features/staff/pager";
import { Link, usePathname, useRouter } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";

type MemberSummary = Schema<"MemberSummary">;

/**
 * Member search for the desk (checklist step 144, MEM-8). The query lives in the URL (?q=), so
 * the staff header's quick lookup and a barcode scanner (which "types" the number and presses
 * Enter) both land here, and results can be bookmarked or shared.
 */
export function MemberSearch() {
  const t = useTranslations("StaffMembers");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const router = useRouter();
  const pathname = usePathname();
  const q = useSearchParams().get("q")?.trim() ?? "";
  const [text, setText] = useState(q);
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ["staff", "members", q, page],
    queryFn: () =>
      api.get<PageResponse<MemberSummary>>("/staff/members", { query: { q, page, size: 20 } }),
    enabled: q.length >= 2,
    placeholderData: keepPreviousData,
  });

  return (
    <div className="grid gap-4">
      <form
        role="search"
        className="flex max-w-xl gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          router.replace(`${pathname}?q=${encodeURIComponent(text.trim())}`);
        }}
      >
        <label htmlFor="member-search" className="sr-only">
          {t("searchLabel")}
        </label>
        <Input
          id="member-search"
          type="search"
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder={t("searchPlaceholder")}
          autoFocus
          aria-describedby="member-search-hint"
        />
        <Button type="submit">
          <Search aria-hidden />
          {t("search")}
        </Button>
      </form>
      <p id="member-search-hint" className="text-sm text-muted-foreground">
        {t("hint")}
      </p>

      {q.length >= 2 && query.isPending && (
        <div className="flex justify-center py-8">
          <HexLoader label={tCommon("loading")} />
        </div>
      )}
      {query.isError && <FormAlert>{errorMessage(query.error)}</FormAlert>}
      {query.data && query.data.totalElements === 0 && (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">
          {t("empty", { q })}
        </p>
      )}
      {query.data && query.data.totalElements > 0 && (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("memberNo")}</TableHead>
                <TableHead>{t("name")}</TableHead>
                <TableHead>{t("contact")}</TableHead>
                <TableHead>{t("type")}</TableHead>
                <TableHead>{t("status")}</TableHead>
                <TableHead>{t("expires")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {query.data.content.map((member) => (
                <TableRow key={member.id}>
                  <TableCell className="font-mono">
                    <Link
                      href={`/staff/members/${member.id}`}
                      className="font-medium underline-offset-4 hover:underline"
                    >
                      {member.memberNo}
                    </Link>
                  </TableCell>
                  <TableCell className="font-medium">{member.fullName}</TableCell>
                  <TableCell className="text-xs">
                    <div>{member.email}</div>
                    {member.phone && <div className="text-muted-foreground">{member.phone}</div>}
                  </TableCell>
                  <TableCell>{member.membershipType}</TableCell>
                  <TableCell>
                    <MemberStatusBadge status={member.status} />
                  </TableCell>
                  <TableCell>
                    {member.expiresAt &&
                      format.dateTime(new Date(member.expiresAt), { dateStyle: "medium" })}
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
