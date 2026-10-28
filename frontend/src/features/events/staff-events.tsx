"use client";

import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
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

export type StaffEvent = Schema<"StaffEventSummary">;

export const STAFF_EVENTS_KEY = ["staff", "events"] as const;

/** Staff events list (checklist step 344): drafts too, latest start first, with sign-ups. */
export function StaffEvents() {
  const t = useTranslations("StaffEvents");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const [page, setPage] = useState(0);
  // When the page opened: tells "ended" events apart
  const [now] = useState(() => Date.now());

  const query = useQuery({
    queryKey: [...STAFF_EVENTS_KEY, page],
    queryFn: () =>
      api.get<PageResponse<StaffEvent>>("/staff/events", { query: { page, size: 20 } }),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="grid gap-4">
      <div>
        <Button asChild>
          <Link href="/staff/events/new">
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
                <TableHead>{t("event")}</TableHead>
                <TableHead>{t("when")}</TableHead>
                <TableHead>{t("status")}</TableHead>
                <TableHead className="text-right">{t("signUps")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {query.data.content.map((event) => (
                <TableRow key={event.id}>
                  <TableCell>
                    <Link
                      href={`/staff/events/${event.id}`}
                      className="font-medium underline-offset-4 hover:underline"
                    >
                      {event.title}
                    </Link>
                    <p className="text-xs text-muted-foreground">{event.venue}</p>
                  </TableCell>
                  <TableCell className="text-sm whitespace-nowrap">
                    {format.dateTime(new Date(event.startsAt), {
                      dateStyle: "medium",
                      timeStyle: "short",
                    })}
                  </TableCell>
                  <TableCell>
                    <StaffEventStatus event={event} now={now} />
                  </TableCell>
                  <TableCell className="text-right text-sm tabular-nums">
                    {event.registrationRequired ? (
                      <>
                        {event.capacity != null
                          ? t("ofCapacity", { count: event.registered, capacity: event.capacity })
                          : event.registered}
                        {event.waitlisted > 0 && (
                          <span className="block text-xs text-muted-foreground">
                            {t("waiting", { count: event.waitlisted })}
                          </span>
                        )}
                      </>
                    ) : (
                      <span className="text-muted-foreground">{t("noSignUp")}</span>
                    )}
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

/** Draft, published, cancelled, or ended (published and over). */
export function StaffEventStatus({ event, now }: { event: StaffEvent; now: number }) {
  const t = useTranslations("StaffEvents.statuses");
  if (event.status === "DRAFT") return <Badge variant="outline">{t("DRAFT")}</Badge>;
  if (event.status === "CANCELLED") return <Badge variant="destructive">{t("CANCELLED")}</Badge>;
  if (new Date(event.endsAt).getTime() <= now)
    return <Badge variant="secondary">{t("ENDED")}</Badge>;
  return <Badge variant="available">{t("PUBLISHED")}</Badge>;
}
