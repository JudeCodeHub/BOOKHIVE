"use client";

import { BackLink } from "@/components/layout/back-link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Ban, Download, ExternalLink, ScanQrCode, Send } from "lucide-react";
import { useParams } from "next/navigation";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { PictureUpload } from "@/components/forms/picture-upload";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage, useErrorMessage } from "@/features/auth/errors";
import { Link } from "@/i18n/navigation";
import { api, type Schema } from "@/lib/api";
import { libraryDay } from "./library-dates";
import { StaffEventForm } from "./staff-event-form";
import { STAFF_EVENTS_KEY, StaffEventStatus, type StaffEvent } from "./staff-events";

type Attendee = Schema<"AttendeeSummary">;

/**
 * One event for staff (checklist step 344): edit the details, publish or cancel, the poster,
 * and who's signed up (with a CSV for the organiser).
 */
export function StaffEventPage() {
  const t = useTranslations("StaffEvents");
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const { id } = useParams<{ id: string }>();
  const [now] = useState(() => Date.now());
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: [...STAFF_EVENTS_KEY, "detail", id],
    queryFn: () => api.get<StaffEvent>(`/staff/events/${id}`),
  });

  if (query.isPending) return <HexLoader label={tCommon("loading")} />;
  if (query.isError) return <FormAlert>{errorMessage(query.error)}</FormAlert>;

  const event = query.data;
  return (
    <div className="grid gap-4">
      <BackLink href="/staff/events">{t("back")}</BackLink>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-xl font-semibold">{event.title}</h1>
        <StaffEventStatus event={event} now={now} />
      </div>
      {event.status === "CANCELLED" && event.cancelReason && (
        <p className="text-sm text-muted-foreground">
          {t("cancelReasonShown", { reason: event.cancelReason })}
        </p>
      )}
      <EventActions event={event} now={now} />

      <Tabs defaultValue="details" className="gap-4">
        <TabsList>
          <TabsTrigger value="details">{t("tabDetails")}</TabsTrigger>
          <TabsTrigger value="poster">{t("tabPoster")}</TabsTrigger>
          {event.registrationRequired && (
            <TabsTrigger value="sign-ups">
              {t("tabSignUps")}
              <Badge variant="secondary">{event.registered + event.waitlisted}</Badge>
            </TabsTrigger>
          )}
        </TabsList>
        <TabsContent value="details">
          <StaffEventForm key={event.id} event={event} />
        </TabsContent>
        <TabsContent value="poster">
          <PictureUpload<StaffEvent>
            path={`/staff/events/${event.id}/image`}
            hasImage={event.hasImage}
            version={event.imageVersion}
            hint={t("posterHint")}
            disabled={event.status === "CANCELLED"}
            onChange={(updated) => {
              queryClient.setQueryData([...STAFF_EVENTS_KEY, "detail", event.id], updated);
            }}
          />
        </TabsContent>
        {event.registrationRequired && (
          <TabsContent value="sign-ups">
            <Attendees event={event} />
          </TabsContent>
        )}
      </Tabs>
    </div>
  );
}

/** Publish (drafts), cancel (with a reason; everyone signed up is told), view, check-in. */
function EventActions({ event, now }: { event: StaffEvent; now: number }) {
  const t = useTranslations("StaffEvents");
  const queryClient = useQueryClient();
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState("");

  const done = (saved: StaffEvent) => {
    queryClient.setQueryData([...STAFF_EVENTS_KEY, "detail", event.id], saved);
    void queryClient.invalidateQueries({ queryKey: STAFF_EVENTS_KEY });
  };
  const errorText = useErrorMessage("StaffEvents");
  const fail = (error: unknown) => toast.error(errorText(error));

  const publish = useMutation({
    mutationFn: () => api.post<StaffEvent>(`/staff/events/${event.id}/publish`),
    onSuccess: (saved) => {
      done(saved);
      toast.success(t("published"));
    },
    onError: fail,
  });
  const cancel = useMutation({
    mutationFn: () =>
      api.post<StaffEvent>(`/staff/events/${event.id}/cancel`, { reason: reason.trim() }),
    onSuccess: (saved) => {
      done(saved);
      setCancelling(false);
      toast.success(t("cancelledToast"));
    },
    onError: fail,
  });

  const ended = new Date(event.endsAt).getTime() <= now;
  const reasonOk = reason.trim().length >= 5 && reason.trim().length <= 500;

  return (
    <div className="flex flex-wrap gap-2">
      {event.status === "DRAFT" && (
        <Button size="sm" onClick={() => publish.mutate()} disabled={publish.isPending}>
          <Send aria-hidden />
          {t("publish")}
        </Button>
      )}
      {event.status === "PUBLISHED" && event.registrationRequired && !ended && (
        <Button size="sm" variant="secondary" asChild>
          <Link href={`/staff/events/${event.id}/check-in`}>
            <ScanQrCode aria-hidden />
            {t("checkIn")}
          </Link>
        </Button>
      )}
      {event.status !== "DRAFT" && (
        <Button size="sm" variant="outline" asChild>
          <Link href={`/events/${event.id}`} target="_blank">
            <ExternalLink aria-hidden />
            {t("viewOnSite")}
          </Link>
        </Button>
      )}
      {event.status !== "CANCELLED" && !ended && (
        <Button size="sm" variant="ghost" onClick={() => setCancelling(true)}>
          <Ban aria-hidden />
          {t("cancelEvent")}
        </Button>
      )}
      <Dialog open={cancelling} onOpenChange={setCancelling}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("cancelTitle")}</DialogTitle>
            <DialogDescription>
              {event.registered + event.waitlisted > 0
                ? t("cancelText", { count: event.registered + event.waitlisted })
                : t("cancelTextNobody")}
            </DialogDescription>
          </DialogHeader>
          <label className="grid gap-1 text-sm">
            {t("cancelReason")}
            <Textarea
              rows={3}
              value={reason}
              maxLength={500}
              onChange={(e) => setReason(e.target.value)}
              placeholder={t("cancelReasonPlaceholder")}
            />
          </label>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancelling(false)}>
              {t("keepEvent")}
            </Button>
            <Button
              variant="destructive"
              disabled={!reasonOk || cancel.isPending}
              onClick={() => cancel.mutate()}
            >
              {t("cancelEvent")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** Who's signed up: places first, then the waitlist; download as CSV. */
function Attendees({ event }: { event: StaffEvent }) {
  const t = useTranslations("StaffEvents");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const [downloading, setDownloading] = useState(false);

  const query = useQuery({
    queryKey: [...STAFF_EVENTS_KEY, "attendees", event.id],
    queryFn: () => api.get<Attendee[]>(`/staff/events/${event.id}/attendees`),
    refetchInterval: 30_000,
  });

  async function downloadCsv() {
    setDownloading(true);
    try {
      const blob = await api.getBlob(`/staff/events/${event.id}/attendees.csv`);
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = `attendees-${libraryDay(event.startsAt)}.csv`;
      link.click();
      URL.revokeObjectURL(link.href);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setDownloading(false);
    }
  }

  if (query.isPending) return <HexLoader label={tCommon("loading")} />;
  if (query.isError) return <FormAlert>{errorMessage(query.error)}</FormAlert>;

  const attended = query.data.filter((a) => a.status === "ATTENDED").length;
  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <p className="text-sm">
          {t("signUpSummary", {
            registered: event.registered,
            attended,
            waitlisted: event.waitlisted,
          })}
          {event.capacity != null && ` · ${t("capacityShown", { count: event.capacity })}`}
        </p>
        <Button
          size="sm"
          variant="outline"
          disabled={downloading || query.data.length === 0}
          onClick={() => void downloadCsv()}
        >
          <Download aria-hidden />
          {t("downloadCsv")}
        </Button>
      </div>
      {query.data.length === 0 ? (
        <p className="rounded-lg border p-6 text-center text-sm text-muted-foreground">
          {t("nobodyYet")}
        </p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("member")}</TableHead>
              <TableHead>{t("contact")}</TableHead>
              <TableHead>{t("status")}</TableHead>
              <TableHead>{t("signedUp")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {query.data.map((a) => (
              <TableRow key={a.registrationId}>
                <TableCell>
                  <span className="font-medium">{a.fullName ?? "–"}</span>
                  <span className="block font-mono text-xs text-muted-foreground">
                    {a.memberNo}
                  </span>
                </TableCell>
                <TableCell className="text-sm">
                  {a.email}
                  {a.phone && <span className="block text-muted-foreground">{a.phone}</span>}
                </TableCell>
                <TableCell>
                  {a.status === "ATTENDED" ? (
                    <Badge variant="available">
                      {t("attendee.ATTENDED", {
                        time: format.dateTime(new Date(a.checkedInAt ?? a.registeredAt), {
                          timeStyle: "short",
                        }),
                      })}
                    </Badge>
                  ) : a.status === "WAITLISTED" ? (
                    <Badge variant="on-loan">
                      {t("attendee.WAITLISTED", { position: a.waitlistPosition ?? 1 })}
                    </Badge>
                  ) : (
                    <Badge variant="outline">{t("attendee.REGISTERED")}</Badge>
                  )}
                </TableCell>
                <TableCell className="text-sm whitespace-nowrap">
                  {format.dateTime(new Date(a.registeredAt), {
                    dateStyle: "medium",
                    timeStyle: "short",
                  })}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
