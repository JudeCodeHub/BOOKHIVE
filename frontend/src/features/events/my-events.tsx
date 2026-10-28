"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, Clock, MapPin, QrCode as QrIcon } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { QrCode } from "@/components/common/qr-code";
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
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";
import { posterUrl } from "./library-dates";

type Registration = Schema<"RegistrationSummary">;

/** Must match EventCheckInService.QR_PREFIX: the door scanner accepts the code with it. */
const QR_PREFIX = "BOOKHIVE-EVENT:";

/**
 * "My events" (checklist step 343): coming up first (soonest first), with the QR ticket to show
 * at the door and a way to give up the place; then past events, newest first.
 */
export function MyEvents() {
  const t = useTranslations("MyEvents");
  const tCommon = useTranslations("Common");
  const errorMessage = useAuthErrorMessage();
  const [ticket, setTicket] = useState<Registration | null>(null);
  const [cancelling, setCancelling] = useState<Registration | null>(null);
  // When the page opened: enough to split "coming up" from "past"
  const [now] = useState(() => Date.now());
  const queryClient = useQueryClient();

  const mine = useQuery({
    queryKey: ["me", "events"],
    queryFn: () => api.get<Registration[]>("/me/events"),
  });

  const cancel = useMutation({
    mutationFn: (r: Registration) => api.post(`/me/events/${r.eventId}/cancel`),
    onSuccess: (_, r) => {
      setCancelling(null);
      toast.success(t("cancelled", { title: r.eventTitle }));
      void queryClient.invalidateQueries({ queryKey: ["me", "events"] });
      void queryClient.invalidateQueries({ queryKey: ["events"] });
    },
    onError: (error) => {
      setCancelling(null);
      const code = error instanceof ApiError ? error.code : undefined;
      toast.error(code === "EVENT_ALREADY_STARTED" ? t("alreadyStarted") : errorMessage(error));
    },
  });

  if (mine.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (mine.isError) {
    if (mine.error instanceof ApiError && mine.error.code === "MEMBER_NOT_FOUND") {
      return (
        <p className="rounded-lg border p-6 text-center text-muted-foreground">
          {t.rich("notMember", {
            link: (chunks) => (
              <Link href="/account/membership" className="font-medium text-primary underline">
                {chunks}
              </Link>
            ),
          })}
        </p>
      );
    }
    return <FormAlert>{errorMessage(mine.error)}</FormAlert>;
  }

  const upcoming = mine.data
    .filter((r) => new Date(r.endsAt).getTime() > now && r.eventStatus !== "CANCELLED")
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const past = mine.data.filter((r) => !upcoming.includes(r));

  if (mine.data.length === 0) {
    return (
      <p className="rounded-lg border p-6 text-center text-muted-foreground">
        {t.rich("empty", {
          link: (chunks) => (
            <Link href="/events" className="font-medium text-primary underline">
              {chunks}
            </Link>
          ),
        })}
      </p>
    );
  }

  return (
    <div className="grid gap-8">
      <section aria-labelledby="coming-up" className="grid gap-3">
        <h2 id="coming-up" className="text-lg font-semibold">
          {t("comingUp")}
        </h2>
        {upcoming.length === 0 ? (
          <p className="text-muted-foreground">
            {t.rich("noneComingUp", {
              link: (chunks) => (
                <Link href="/events" className="font-medium text-primary underline">
                  {chunks}
                </Link>
              ),
            })}
          </p>
        ) : (
          <ul className="grid gap-3">
            {upcoming.map((r) => (
              <li key={r.registrationId}>
                <EventRow registration={r} now={now}>
                  {r.status === "REGISTERED" && r.checkInCode && (
                    <Button size="sm" onClick={() => setTicket(r)}>
                      <QrIcon aria-hidden />
                      {t("showTicket")}
                    </Button>
                  )}
                  {(r.status === "REGISTERED" || r.status === "WAITLISTED") &&
                    new Date(r.startsAt).getTime() > now && (
                      <Button size="sm" variant="ghost" onClick={() => setCancelling(r)}>
                        {r.status === "WAITLISTED" ? t("leaveWaitlist") : t("cancelPlace")}
                      </Button>
                    )}
                </EventRow>
              </li>
            ))}
          </ul>
        )}
      </section>

      {past.length > 0 && (
        <section aria-labelledby="past" className="grid gap-3">
          <h2 id="past" className="text-lg font-semibold">
            {t("past")}
          </h2>
          <ul className="grid gap-3">
            {past.map((r) => (
              <li key={r.registrationId}>
                <EventRow registration={r} now={now} />
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* The ticket: big, black on white, to show on the phone at the door */}
      <Dialog open={ticket !== null} onOpenChange={(open) => !open && setTicket(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{ticket?.eventTitle}</DialogTitle>
            <DialogDescription>{t("ticketText")}</DialogDescription>
          </DialogHeader>
          {ticket?.checkInCode && (
            <div className="grid justify-items-center gap-2">
              <QrCode
                value={QR_PREFIX + ticket.checkInCode}
                label={t("ticketLabel", { title: ticket.eventTitle })}
                className="w-full max-w-64 rounded-lg"
              />
              {/* Typed in at the desk if the camera can't read the screen */}
              <p className="font-mono text-lg tracking-widest">{ticket.checkInCode}</p>
            </div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={cancelling !== null} onOpenChange={(open) => !open && setCancelling(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {cancelling?.status === "WAITLISTED"
                ? t("leaveWaitlistTitle")
                : t("cancelPlaceTitle")}
            </DialogTitle>
            <DialogDescription>
              {t("cancelText", { title: cancelling?.eventTitle ?? "" })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancelling(null)}>
              {t("keep")}
            </Button>
            <Button
              variant="destructive"
              disabled={cancel.isPending}
              onClick={() => cancelling && cancel.mutate(cancelling)}
            >
              {cancelling?.status === "WAITLISTED" ? t("leaveWaitlist") : t("cancelPlace")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** One sign-up: poster, title, when and where, status, and the buttons passed in. */
function EventRow({
  registration: r,
  now,
  children,
}: {
  registration: Registration;
  now: number;
  children?: React.ReactNode;
}) {
  const format = useFormatter();
  const poster = posterUrl({ id: r.eventId, imageVersion: r.imageVersion });
  const start = new Date(r.startsAt);
  const end = new Date(r.endsAt);

  return (
    <article className="flex gap-4 rounded-lg border bg-card p-3">
      {poster ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={poster}
          alt=""
          loading="lazy"
          className="hidden aspect-video w-32 shrink-0 rounded object-cover sm:block"
        />
      ) : (
        <div
          aria-hidden
          className="hidden w-20 shrink-0 flex-col items-center justify-center rounded bg-primary/10 text-primary sm:flex"
        >
          <span className="font-heading text-2xl leading-none font-semibold">
            {format.dateTime(start, { day: "numeric" })}
          </span>
          <span className="text-xs font-medium uppercase">
            {format.dateTime(start, { month: "short" })}
          </span>
        </div>
      )}
      <div className="grid min-w-0 flex-1 content-start gap-1.5">
        <h3 className="font-heading font-semibold">
          <Link href={`/events/${r.eventId}`} className="hover:underline">
            {r.eventTitle}
          </Link>
        </h3>
        <ul className="grid gap-0.5 text-sm text-muted-foreground">
          <li className="flex items-center gap-2">
            <CalendarDays aria-hidden className="size-4 shrink-0" />
            {format.dateTime(start, { weekday: "short", day: "numeric", month: "long" })}
          </li>
          <li className="flex items-center gap-2">
            <Clock aria-hidden className="size-4 shrink-0" />
            {format.dateTimeRange(start, end, { hour: "numeric", minute: "2-digit" })}
          </li>
          <li className="flex items-center gap-2">
            <MapPin aria-hidden className="size-4 shrink-0" />
            {r.venue}
          </li>
        </ul>
        <div>
          <StatusBadge registration={r} now={now} />
        </div>
        {children && <div className="flex flex-wrap gap-2 pt-1">{children}</div>}
      </div>
    </article>
  );
}

function StatusBadge({ registration: r, now }: { registration: Registration; now: number }) {
  const t = useTranslations("MyEvents");
  if (r.eventStatus === "CANCELLED") {
    return <Badge variant="destructive">{t("status.eventCancelled")}</Badge>;
  }
  if (r.status === "ATTENDED") return <Badge variant="available">{t("status.attended")}</Badge>;
  if (r.status === "WAITLISTED") {
    return new Date(r.endsAt).getTime() > now ? (
      <Badge variant="on-loan">
        {t("status.waitlisted", { position: r.waitlistPosition ?? 1 })}
      </Badge>
    ) : (
      <Badge variant="outline">{t("status.noPlace")}</Badge>
    );
  }
  return new Date(r.endsAt).getTime() > now ? (
    <Badge variant="available">{t("status.registered")}</Badge>
  ) : (
    <Badge variant="outline">{t("status.notCheckedIn")}</Badge>
  );
}
