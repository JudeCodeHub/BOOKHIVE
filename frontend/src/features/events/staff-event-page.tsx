"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  Ban,
  Download,
  ExternalLink,
  ImageUp,
  ScanQrCode,
  Send,
  Trash2,
} from "lucide-react";
import { useParams } from "next/navigation";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { UploadProgress } from "@/components/forms/file-upload";
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
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";
import { libraryDay } from "./library-dates";
import { StaffEventForm } from "./staff-event-form";
import { STAFF_EVENTS_KEY, StaffEventStatus, type StaffEvent } from "./staff-events";

type Attendee = Schema<"AttendeeSummary">;

const MAX_POSTER_BYTES = 5 * 1024 * 1024;

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

  const query = useQuery({
    queryKey: [...STAFF_EVENTS_KEY, "detail", id],
    queryFn: () => api.get<StaffEvent>(`/staff/events/${id}`),
  });

  if (query.isPending) return <HexLoader label={tCommon("loading")} />;
  if (query.isError) return <FormAlert>{errorMessage(query.error)}</FormAlert>;

  const event = query.data;
  return (
    <div className="grid gap-4">
      <Link
        href="/staff/events"
        className="inline-flex items-center gap-1 justify-self-start text-sm text-muted-foreground underline-offset-4 hover:underline"
      >
        <ArrowLeft aria-hidden className="size-4" />
        {t("back")}
      </Link>
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
          <EventPoster event={event} />
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
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState("");

  const done = (saved: StaffEvent) => {
    queryClient.setQueryData([...STAFF_EVENTS_KEY, "detail", event.id], saved);
    void queryClient.invalidateQueries({ queryKey: STAFF_EVENTS_KEY });
  };
  const fail = (error: unknown) => {
    const code = error instanceof ApiError ? error.code : undefined;
    toast.error(
      code && t.has(`errors.${code}` as never) ? t(`errors.${code}` as never) : errorMessage(error),
    );
  };

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

/** The poster: shown as staff see it (drafts too), replace or remove. */
function EventPoster({ event }: { event: StaffEvent }) {
  const t = useTranslations("StaffEvents");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  const image = useQuery({
    // A new upload changes the summary (refetched), so key on the event's data
    queryKey: [...STAFF_EVENTS_KEY, "poster", event.id, event.hasImage],
    queryFn: () => api.getBlob(`/staff/events/${event.id}/image`),
    enabled: event.hasImage,
    staleTime: Infinity,
  });
  const url = useMemo(() => (image.data ? URL.createObjectURL(image.data) : null), [image.data]);
  useEffect(
    () => () => {
      if (url) URL.revokeObjectURL(url);
    },
    [url],
  );

  const saved = (updated: StaffEvent) => {
    queryClient.setQueryData([...STAFF_EVENTS_KEY, "detail", event.id], updated);
    void queryClient.invalidateQueries({ queryKey: [...STAFF_EVENTS_KEY, "poster", event.id] });
  };

  async function upload(file: File | undefined) {
    if (inputRef.current) inputRef.current.value = "";
    if (!file) return;
    if (!["image/jpeg", "image/png"].includes(file.type)) return toast.error(t("posterType"));
    if (file.size > MAX_POSTER_BYTES) return toast.error(t("posterTooLarge"));
    setBusy(true);
    try {
      const body = new FormData();
      body.append("image", file);
      saved(
        await api.post<StaffEvent>(`/staff/events/${event.id}/image`, body, {
          onUploadProgress: setProgress,
        }),
      );
      toast.success(t("posterSaved"));
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
      setProgress(null);
    }
  }

  async function remove() {
    setBusy(true);
    try {
      saved(await api.delete<StaffEvent>(`/staff/events/${event.id}/image`));
      toast.success(t("posterRemoved"));
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid max-w-xl justify-items-start gap-3">
      <p className="text-sm text-muted-foreground">{t("posterHint")}</p>
      {url ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt={t("posterCurrent")} className="w-full rounded-lg border" />
      ) : (
        <div className="flex aspect-video w-full items-center justify-center rounded-lg border border-dashed text-sm text-muted-foreground">
          {event.hasImage ? <HexLoader label={t("posterCurrent")} /> : t("posterNone")}
        </div>
      )}
      {progress !== null && <UploadProgress value={progress} />}
      {event.status !== "CANCELLED" && (
        <div className="flex flex-wrap gap-2">
          <Button
            size="sm"
            variant="secondary"
            disabled={busy}
            onClick={() => inputRef.current?.click()}
          >
            <ImageUp aria-hidden />
            {event.hasImage ? t("posterReplace") : t("posterUpload")}
          </Button>
          {event.hasImage && (
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => void remove()}>
              <Trash2 aria-hidden />
              {t("posterRemove")}
            </Button>
          )}
        </div>
      )}
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png"
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        onChange={(e) => void upload(e.target.files?.[0])}
      />
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
