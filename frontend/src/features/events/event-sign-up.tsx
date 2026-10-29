"use client";

import { useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarCheck, CircleCheck, Hourglass, UserPlus } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useErrorMessage } from "@/features/auth/errors";
import { useSession } from "@/features/auth/use-session";
import { Link } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";
import { PlacesBadge } from "./event-card";
import type { LibraryEvent } from "./library-dates";

type Registration = Schema<"RegistrationSummary">;

/**
 * The sign-up box on the event page (EVT-2, checklist step 342). Places left and the member's
 * own sign-up are loaded fresh here (the rest of the page is cached). Signs up for a place, or
 * for the waitlist when the event is full; a place or the waitlist can be given up before the
 * event starts.
 */
export function EventSignUp({ initial }: { initial: LibraryEvent }) {
  const t = useTranslations("EventPage");
  const tCommon = useTranslations("Common");
  const queryClient = useQueryClient();
  const session = useSession();
  const loggedIn = session.status === "authenticated";
  const [confirmCancel, setConfirmCancel] = useState(false);

  const live = useQuery({
    queryKey: ["events", initial.id],
    queryFn: () => api.get<LibraryEvent>(`/events/${initial.id}`),
    initialData: initial,
    // The server copy may be a few minutes old: fetch again straight away, then every minute
    initialDataUpdatedAt: 0,
    refetchInterval: 60_000,
  });
  const event = live.data;

  const mine = useQuery({
    queryKey: ["me", "events", initial.id],
    // 204 (not signed up) comes back as undefined; TanStack Query needs null instead
    queryFn: async () =>
      (await api.get<Registration | undefined>(`/me/events/${initial.id}/registration`)) ?? null,
    enabled: loggedIn && event.registrationRequired,
    retry: false,
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ["events"] });
    void queryClient.invalidateQueries({ queryKey: ["me", "events"] });
  };
  const errorText = useErrorMessage("EventPage");

  const register = useMutation({
    mutationFn: () => api.post<Registration>(`/me/events/${initial.id}/register`),
    onSuccess: (r) => {
      queryClient.setQueryData(["me", "events", initial.id], r);
      toast.success(
        r.status === "WAITLISTED"
          ? t("joinedWaitlist", { position: r.waitlistPosition ?? 1 })
          : t("gotPlace"),
      );
      refresh();
    },
    onError: (error) => {
      toast.error(errorText(error));
      refresh();
    },
  });

  const cancel = useMutation({
    mutationFn: () => api.post<void>(`/me/events/${initial.id}/cancel`),
    onSuccess: () => {
      queryClient.setQueryData(["me", "events", initial.id], null);
      setConfirmCancel(false);
      toast.success(t("cancelled"));
      refresh();
    },
    onError: (error) => {
      setConfirmCancel(false);
      toast.error(errorText(error));
      refresh();
    },
  });

  const box = (children: ReactNode) => (
    <section aria-labelledby="sign-up" className="grid gap-3 rounded-lg border p-4">
      <h2 id="sign-up" className="text-sm font-semibold">
        {t("signUp")}
      </h2>
      {children}
    </section>
  );

  if (event.status === "CANCELLED") return null;
  if (!event.registrationRequired) {
    return box(<p className="text-sm">{t("noSignUpNeeded")}</p>);
  }

  const placesLine = (
    <div className="grid gap-1 text-sm">
      <div>
        <PlacesBadge event={event} />
      </div>
      {event.capacity != null && (
        <p className="text-muted-foreground">
          {t("capacity", { count: event.capacity })}
          {event.waitlisted > 0 && ` · ${t("waiting", { count: event.waitlisted })}`}
        </p>
      )}
    </div>
  );
  const full = event.placesLeft === 0;

  if (session.status === "loading" || (loggedIn && mine.isPending)) {
    return box(<HexLoader label={tCommon("loading")} />);
  }

  // Already signed up (shown even after sign-up has closed, so they know where they stand)
  if (mine.data) {
    const r = mine.data;
    const started = !event.openForRegistration;
    return box(
      <>
        {r.status === "WAITLISTED" ? (
          <p className="flex items-start gap-2 text-sm">
            <Hourglass aria-hidden className="mt-0.5 size-4 shrink-0 text-on-loan-foreground" />
            {t("onWaitlist", { position: r.waitlistPosition ?? 1 })}
          </p>
        ) : (
          <p className="flex items-start gap-2 text-sm">
            <CircleCheck aria-hidden className="mt-0.5 size-4 shrink-0 text-available-foreground" />
            {r.status === "ATTENDED" ? t("attended") : t("havePlaceText")}
          </p>
        )}
        {r.status === "REGISTERED" && (
          <Button asChild variant="outline">
            <Link href="/account/events">
              <CalendarCheck aria-hidden />
              {t("myEvents")}
            </Link>
          </Button>
        )}
        {!started && r.status !== "ATTENDED" && (
          <Button variant="ghost" onClick={() => setConfirmCancel(true)}>
            {r.status === "WAITLISTED" ? t("leaveWaitlist") : t("cancelPlace")}
          </Button>
        )}
        <Dialog open={confirmCancel} onOpenChange={setConfirmCancel}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>
                {r.status === "WAITLISTED" ? t("leaveWaitlistTitle") : t("cancelPlaceTitle")}
              </DialogTitle>
              <DialogDescription>{t("cancelText")}</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="outline" onClick={() => setConfirmCancel(false)}>
                {t("keep")}
              </Button>
              <Button
                variant="destructive"
                onClick={() => cancel.mutate()}
                disabled={cancel.isPending}
              >
                {r.status === "WAITLISTED" ? t("leaveWaitlist") : t("cancelPlace")}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </>,
    );
  }

  if (!event.openForRegistration) {
    return box(
      <>
        {placesLine}
        <p className="text-sm text-muted-foreground">{t("closedText")}</p>
      </>,
    );
  }

  if (!loggedIn) {
    return box(
      <>
        {placesLine}
        <Button asChild>
          <Link href={`/login?next=${encodeURIComponent(`/events/${event.id}`)}`}>
            {t("logInToSignUp")}
          </Link>
        </Button>
      </>,
    );
  }

  if (mine.isError) {
    const notMember = mine.error instanceof ApiError && mine.error.code === "MEMBER_NOT_FOUND";
    return box(
      <>
        {placesLine}
        <p className="text-sm">
          {notMember
            ? t.rich("notMember", {
                link: (chunks) => (
                  <Link href="/account/membership" className="font-medium text-primary underline">
                    {chunks}
                  </Link>
                ),
              })
            : errorText(mine.error)}
        </p>
      </>,
    );
  }

  return box(
    <>
      {placesLine}
      {full && <p className="text-sm text-muted-foreground">{t("waitlistText")}</p>}
      <Button
        onClick={() => register.mutate()}
        disabled={register.isPending}
        variant={full ? "outline" : "default"}
      >
        {full ? <Hourglass aria-hidden /> : <UserPlus aria-hidden />}
        {register.isPending ? t("signingUp") : full ? t("joinWaitlist") : t("signUpButton")}
      </Button>
    </>,
  );
}
