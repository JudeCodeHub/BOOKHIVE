"use client";

import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { TimeAgo } from "@/components/common/time-ago";
import { HexLoader } from "@/components/brand/hex-loader";
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

type ApplicationSummary = Schema<"ApplicationSummary">;

export const APPLICATIONS_QUERY_KEY = ["staff", "applications"] as const;

/** Whole years from an ISO date until today. */
export function yearsSince(isoDate: string) {
  const born = new Date(isoDate);
  const now = new Date();
  let years = now.getFullYear() - born.getFullYear();
  if (
    now.getMonth() < born.getMonth() ||
    (now.getMonth() === born.getMonth() && now.getDate() < born.getDate())
  ) {
    years -= 1;
  }
  return years;
}

/** Pending membership applications, oldest first (checklist step 142). */
export function ApplicationsQueue() {
  const t = useTranslations("StaffApplications");
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: [...APPLICATIONS_QUERY_KEY, page],
    queryFn: () =>
      api.get<PageResponse<ApplicationSummary>>("/staff/applications", {
        query: { page, size: 20 },
      }),
    // Keep showing the current page while the next one loads
    placeholderData: keepPreviousData,
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
  if (query.data.totalElements === 0) {
    return <p className="rounded-lg border p-6 text-center text-muted-foreground">{t("empty")}</p>;
  }

  return (
    <div className="grid gap-4">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("name")}</TableHead>
            <TableHead>{t("type")}</TableHead>
            <TableHead>{t("age")}</TableHead>
            <TableHead>{t("guardian")}</TableHead>
            <TableHead>{t("applied")}</TableHead>
            <TableHead>
              <span className="sr-only">{t("review")}</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {query.data.content.map((application) => (
            <TableRow key={application.id}>
              <TableCell>
                <div className="font-medium">{application.fullName}</div>
                <div className="text-xs text-muted-foreground">{application.email}</div>
              </TableCell>
              <TableCell>{application.membershipType}</TableCell>
              <TableCell>{yearsSince(application.dateOfBirth)}</TableCell>
              <TableCell className="font-mono">{application.guardianMemberNo ?? "–"}</TableCell>
              <TableCell>
                <time dateTime={application.appliedAt} title={application.appliedAt}>
                  <TimeAgo date={application.appliedAt} />
                </time>
              </TableCell>
              <TableCell className="text-right">
                <Button asChild size="sm">
                  <Link href={`/staff/applications/${application.id}`}>{t("review")}</Link>
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <Pager page={page} totalPages={query.data.totalPages} onChange={setPage} />
    </div>
  );
}
