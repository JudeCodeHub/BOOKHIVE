"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
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
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { refusedRenewal } from "@/features/loans/renewal-reasons";
import { Pager } from "@/features/staff/pager";
import { Link, usePathname, useRouter } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";

type OpenLoan = Schema<"OpenLoanSummary">;
type Renewal = Schema<"LoanRenewalResponse">;

const VIEWS = ["all", "dueSoon", "overdue"] as const;
type View = (typeof VIEWS)[number];

/** "Due soon" = due today or in the next this-many days. */
const DUE_SOON_DAYS = 3;

/**
 * Staff loans list (checklist step 243): everything out now, what's due soon, or what's overdue
 * (longest first, with the phone number to call). The view and page are in the URL, so the
 * overdue list can be bookmarked. Each loan can be renewed from here; an admin override for
 * refused renewals comes with step 246.
 */
export function StaffLoans() {
  const t = useTranslations("StaffLoans");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const view: View = VIEWS.find((v) => v === params.get("view")) ?? "all";
  const page = Math.max(0, Number(params.get("page")) || 0);

  const go = (next: { view?: View; page?: number }) => {
    const v = next.view ?? view;
    const p = next.page ?? 0;
    const search = new URLSearchParams();
    if (v !== "all") search.set("view", v);
    if (p > 0) search.set("page", String(p));
    const query = search.toString();
    router.replace(query ? `${pathname}?${query}` : pathname);
  };

  const loans = useQuery({
    queryKey: ["staff", "loans", view, page],
    queryFn: () =>
      api.get<PageResponse<OpenLoan>>("/staff/loans", {
        query: {
          overdue: view === "overdue",
          ...(view === "dueSoon" ? { dueWithinDays: DUE_SOON_DAYS } : {}),
          page,
          size: 25,
        },
      }),
    placeholderData: keepPreviousData,
  });

  const renew = useMutation({
    mutationFn: (loan: OpenLoan) => api.post<Renewal>(`/staff/loans/${loan.loan.loanId}/renew`, {}),
    onSuccess: (renewal) => {
      toast.success(
        t("renewed", {
          title: renewal.title,
          date: format.dateTime(new Date(renewal.dueDate), { dateStyle: "medium" }),
        }),
      );
      void queryClient.invalidateQueries({ queryKey: ["staff", "loans"] });
    },
    onError: (error) => {
      const refused = refusedRenewal(error);
      if (refused) {
        toast.error(t("renewRefused"), {
          description: refused.problems.map((p) => t(`renewalProblems.${p.reason}`)).join(" "),
        });
      } else {
        toast.error(errorMessage(error));
      }
    },
  });

  const date = (iso: string) => format.dateTime(new Date(iso), { dateStyle: "medium" });
  const data = loans.data;

  return (
    <div className="grid gap-4">
      <Tabs value={view} onValueChange={(next) => go({ view: next as View })}>
        <TabsList aria-label={t("views")}>
          <TabsTrigger value="all">{t("all")}</TabsTrigger>
          <TabsTrigger value="dueSoon">{t("dueSoon", { days: DUE_SOON_DAYS })}</TabsTrigger>
          <TabsTrigger value="overdue">{t("overdue")}</TabsTrigger>
        </TabsList>
      </Tabs>

      {loans.isPending && (
        <div className="flex justify-center py-8">
          <HexLoader label={tCommon("loading")} />
        </div>
      )}
      {loans.isError && <FormAlert>{errorMessage(loans.error)}</FormAlert>}
      {data && data.totalElements === 0 && (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">
          {t(`empty.${view}`)}
        </p>
      )}
      {data && data.totalElements > 0 && (
        <>
          <p className="text-sm text-muted-foreground">
            {t("count", { count: data.totalElements })}
          </p>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("book")}</TableHead>
                <TableHead>{t("member")}</TableHead>
                <TableHead>{t("due")}</TableHead>
                <TableHead>{t("status")}</TableHead>
                <TableHead>{t("renewals")}</TableHead>
                <TableHead>
                  <span className="sr-only">{t("actions")}</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((row) => (
                <TableRow key={row.loan.loanId}>
                  <TableCell>
                    <div className="font-medium">{row.loan.title}</div>
                    <div className="font-mono text-xs text-muted-foreground">
                      {row.loan.barcode}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Link
                      href={`/staff/members/${row.memberId}`}
                      className="font-medium underline-offset-4 hover:underline"
                    >
                      {row.fullName}
                    </Link>
                    <div className="text-xs text-muted-foreground">
                      <span className="font-mono">{row.memberNo}</span>
                      {row.phone && (
                        <>
                          {" · "}
                          <a href={`tel:${row.phone}`} className="hover:underline">
                            {row.phone}
                          </a>
                        </>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="whitespace-nowrap">{date(row.loan.dueDate)}</TableCell>
                  <TableCell>
                    {row.loan.overdue ? (
                      <Badge variant="overdue">
                        {t("daysOverdue", { count: row.daysOverdue })}
                      </Badge>
                    ) : (
                      <Badge variant="on-loan">{t("onLoan")}</Badge>
                    )}
                  </TableCell>
                  <TableCell>{row.loan.renewCount}</TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={renew.isPending && renew.variables?.loan.loanId === row.loan.loanId}
                      onClick={() => renew.mutate(row)}
                    >
                      <RefreshCw aria-hidden />
                      {t("renew")}
                      <span className="sr-only">: {row.loan.title}</span>
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pager page={page} totalPages={data.totalPages} onChange={(p) => go({ page: p })} />
        </>
      )}
    </div>
  );
}
