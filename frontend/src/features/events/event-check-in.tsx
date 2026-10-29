"use client";

import { BackLink } from "@/components/layout/back-link";
import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Camera, CameraOff, CircleAlert, CircleCheck } from "lucide-react";
import { useParams } from "next/navigation";
import { useFormatter, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage, useErrorMessage } from "@/features/auth/errors";
import { CameraView } from "@/features/desk/camera-scanner";
import { playSound } from "@/features/desk/sounds";
import { ApiError, api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";
import { STAFF_EVENTS_KEY, type StaffEvent } from "./staff-events";

type CheckInResponse = Schema<"CheckInResponse">;

/** The same ticket in front of the camera: try it again only after this long. */
const SAME_TICKET_COOLDOWN_MS = 8000;

/** Tickets are case-sensitive codes: only trim them. */
const keepAsRead = (code: string) => code;

type Outcome =
  { kind: "ok"; result: CheckInResponse } | { kind: "problem"; code: string; message: string };

/**
 * Check-in at the door (EVT-4, checklist step 345). Staff point a phone or tablet at the
 * member's QR ticket (My events), or type the code under it. Each result is shown big, in
 * colour, with a sound, so the queue keeps moving without reading small text.
 */
export function EventCheckIn() {
  const t = useTranslations("CheckIn");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorMessage = useAuthErrorMessage();
  const errorText = useErrorMessage("CheckIn");
  const queryClient = useQueryClient();
  const { id } = useParams<{ id: string }>();
  const [cameraOn, setCameraOn] = useState(true);
  const [typed, setTyped] = useState("");
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [recent, setRecent] = useState<CheckInResponse[]>([]);
  const [attended, setAttended] = useState<number | null>(null);
  const busy = useRef(false);

  const event = useQuery({
    queryKey: [...STAFF_EVENTS_KEY, "detail", id],
    queryFn: () => api.get<StaffEvent>(`/staff/events/${id}`),
  });

  const checkIn = useMutation({
    mutationFn: (code: string) =>
      api.post<CheckInResponse>(`/staff/events/${id}/check-in`, { code }),
    onSuccess: (result) => {
      playSound("success");
      setOutcome({ kind: "ok", result });
      setAttended(result.attended);
      setRecent((list) => [result, ...list].slice(0, 10));
      void queryClient.invalidateQueries({ queryKey: [...STAFF_EVENTS_KEY, "attendees", id] });
    },
    onError: (error) => {
      const code = error instanceof ApiError ? (error.code ?? "") : "";
      // Already in: not a problem for the queue, just a different sound
      playSound(code === "ALREADY_CHECKED_IN" ? "warning" : "error");
      setOutcome({
        kind: "problem",
        code,
        message: errorText(error),
      });
    },
    onSettled: () => {
      busy.current = false;
    },
  });

  function submit(code: string) {
    const trimmed = code.trim();
    // One at a time: the camera can read the same ticket again while the first is on its way
    if (!trimmed || busy.current) return;
    busy.current = true;
    checkIn.mutate(trimmed);
  }

  if (event.isPending) return <HexLoader label={tCommon("loading")} />;
  if (event.isError) return <FormAlert>{errorMessage(event.error)}</FormAlert>;

  const e = event.data;
  const count = attended ?? null;

  return (
    <div className="grid gap-4">
      <BackLink href={`/staff/events/${id}`}>{t("back")}</BackLink>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">
            {e.title} ·{" "}
            {format.dateTimeRange(new Date(e.startsAt), new Date(e.endsAt), {
              day: "numeric",
              month: "short",
              hour: "numeric",
              minute: "2-digit",
            })}
          </p>
        </div>
        <p className="text-sm" aria-live="polite">
          {count !== null
            ? e.capacity != null
              ? t("countOf", { count, capacity: e.capacity })
              : t("count", { count })
            : t("signedUp", { count: e.registered })}
        </p>
      </div>

      {e.status !== "PUBLISHED" && (
        <FormAlert>
          {e.status === "CANCELLED" ? t("errors.cancelled") : t("errors.draft")}
        </FormAlert>
      )}

      <div className="grid gap-4 lg:grid-cols-[1fr_20rem]">
        <div className="grid content-start gap-3">
          {cameraOn ? (
            <CameraView
              onScan={submit}
              formats={["qr_code"]}
              normalize={keepAsRead}
              cooldownMs={SAME_TICKET_COOLDOWN_MS}
              showLastCode={false}
            />
          ) : (
            <div className="flex aspect-video items-center justify-center rounded-lg border border-dashed text-sm text-muted-foreground">
              {t("cameraOff")}
            </div>
          )}
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={() => setCameraOn((on) => !on)}>
              {cameraOn ? <CameraOff aria-hidden /> : <Camera aria-hidden />}
              {cameraOn ? t("turnCameraOff") : t("turnCameraOn")}
            </Button>
          </div>
          <form
            className="flex gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              submit(typed);
              setTyped("");
            }}
          >
            <label htmlFor="ticket-code" className="sr-only">
              {t("typeCode")}
            </label>
            <Input
              id="ticket-code"
              value={typed}
              onChange={(event) => setTyped(event.target.value)}
              placeholder={t("typeCode")}
              autoComplete="off"
              autoCapitalize="off"
              spellCheck={false}
              className="font-mono"
            />
            <Button type="submit" disabled={!typed.trim() || checkIn.isPending}>
              {t("checkIn")}
            </Button>
          </form>
        </div>

        <div className="grid content-start gap-3">
          <ResultPanel outcome={outcome} pending={checkIn.isPending} />
          {recent.length > 0 && (
            <section aria-labelledby="recent" className="grid gap-2">
              <h2 id="recent" className="text-sm font-semibold">
                {t("recent")}
              </h2>
              <ul className="grid gap-1 text-sm">
                {recent.map((r) => (
                  <li key={r.registrationId} className="flex justify-between gap-2">
                    <span className="truncate">{r.fullName ?? r.memberNo}</span>
                    <span className="shrink-0 text-muted-foreground tabular-nums">
                      {format.dateTime(new Date(r.checkedInAt), { timeStyle: "short" })}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      </div>
    </div>
  );
}

/** Big and coloured, readable from a step away; read out by screen readers too. */
function ResultPanel({ outcome, pending }: { outcome: Outcome | null; pending: boolean }) {
  const t = useTranslations("CheckIn");
  return (
    <div
      role="status"
      aria-live="assertive"
      className={cn(
        "grid min-h-36 content-center justify-items-center gap-2 rounded-lg border p-4 text-center",
        outcome?.kind === "ok" && "border-available/40 bg-available/10",
        outcome?.kind === "problem" &&
          (outcome.code === "ALREADY_CHECKED_IN"
            ? "border-on-loan/40 bg-on-loan/10"
            : "border-destructive/40 bg-destructive/10"),
      )}
    >
      {pending ? (
        <p className="text-muted-foreground">{t("checking")}</p>
      ) : !outcome ? (
        <p className="text-muted-foreground">{t("waiting")}</p>
      ) : outcome.kind === "ok" ? (
        <>
          <CircleCheck aria-hidden className="size-10 text-available-foreground" />
          <p className="text-lg font-semibold">
            {t("welcome", { name: outcome.result.fullName ?? "" })}
          </p>
          <p className="font-mono text-sm text-muted-foreground">{outcome.result.memberNo}</p>
        </>
      ) : (
        <>
          <CircleAlert
            aria-hidden
            className={cn(
              "size-10",
              outcome.code === "ALREADY_CHECKED_IN"
                ? "text-on-loan-foreground"
                : "text-destructive",
            )}
          />
          <p className="font-semibold">{outcome.message}</p>
        </>
      )}
    </div>
  );
}
