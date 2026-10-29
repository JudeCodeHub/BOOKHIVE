"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, Plus, Trash2 } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage, useErrorMessage } from "@/features/auth/errors";
import { addDays, dayAsDate, libraryDay, weekdayIndex } from "@/features/events/library-dates";
import { api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type DayName = Schema<"DaySummary">["day"];
/** A closed day has no times, whatever the generated type says. */
type Day = { day: DayName; opensAt: string | null; closesAt: string | null };
type Holiday = Schema<"AdminHolidaySummary">;

const DAYS: DayName[] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];
/** A Monday, to get weekday names from the formatter. */
const A_MONDAY = "2024-01-01";

const HOURS_KEY = ["admin", "hours"] as const;
const HOLIDAYS_KEY = ["admin", "holidays"] as const;

/**
 * Opening hours and holidays (ADM-3, checklist step 370). Days that become closed move the due
 * dates and pickup days on them to the next open day (the API does that), so the page says so.
 */
export function HoursAndHolidays() {
  const t = useTranslations("AdminHours");
  // Today in Sri Lanka, read once: past days can't get a new holiday
  const [today] = useState(() => libraryDay(new Date()));
  return (
    <div className="grid gap-8">
      <p className="max-w-3xl rounded-md border bg-muted/40 p-3 text-sm">{t("movesNote")}</p>
      <WeeklyHours />
      <Holidays today={today} />
    </div>
  );
}

function WeeklyHours() {
  const t = useTranslations("AdminHours");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const errorText = useErrorMessage("AdminHours");
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<Day[] | null>(null);

  const query = useQuery({
    queryKey: HOURS_KEY,
    queryFn: () => api.get<{ week: Day[] }>("/admin/hours"),
  });
  const save = useMutation({
    mutationFn: (week: Day[]) => api.put<{ week: Day[] }>("/admin/hours", { week }),
    onSuccess: (saved) => {
      queryClient.setQueryData(HOURS_KEY, saved);
      void queryClient.invalidateQueries({ queryKey: ["library", "hours"] });
      setDraft(null);
      toast.success(t("hoursSaved"));
    },
    onError: (error) => toast.error(errorText(error)),
  });

  if (query.isPending) return <HexLoader label={tCommon("loading")} />;
  if (query.isError) return <FormAlert>{errorText(query.error)}</FormAlert>;

  const short = (time: string | null) => (time ? time.slice(0, 5) : null);
  const saved: Day[] = DAYS.map((name) => {
    const d = query.data.week.find((x) => x.day === name);
    return { day: name, opensAt: short(d?.opensAt ?? null), closesAt: short(d?.closesAt ?? null) };
  });
  const week = draft ?? saved;
  const dirty = draft !== null && JSON.stringify(draft) !== JSON.stringify(saved);
  const badDay = week.find((d) => d.opensAt && d.closesAt && d.opensAt >= d.closesAt);
  const neverOpen = week.every((d) => !d.opensAt);
  const update = (index: number, change: Partial<Day>) =>
    setDraft(week.map((d, i) => (i === index ? { ...d, ...change } : d)));

  return (
    <section aria-labelledby="weekly-hours" className="grid gap-3 rounded-lg border bg-card p-5">
      <h2 id="weekly-hours" className="font-semibold">
        {t("weekTitle")}
      </h2>
      <p className="text-sm text-muted-foreground">{t("weekHint")}</p>
      <ul className="grid gap-2">
        {week.map((d, i) => {
          const open = d.opensAt !== null;
          const invalid = badDay === d;
          const dayName = format.dateTime(dayAsDate(addDays(A_MONDAY, i)), { weekday: "long" });
          return (
            <li key={d.day} className="flex flex-wrap items-center gap-3">
              <span className="w-28 text-sm font-medium">{dayName}</span>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  className="size-4 accent-primary"
                  checked={open}
                  onChange={(e) =>
                    update(
                      i,
                      e.target.checked
                        ? { opensAt: "08:00", closesAt: "17:00" }
                        : { opensAt: null, closesAt: null },
                    )
                  }
                />
                {t("open")}
              </label>
              {open ? (
                <span className="flex items-center gap-2">
                  <Input
                    type="time"
                    aria-label={t("opensAt", { day: dayName })}
                    value={d.opensAt ?? ""}
                    onChange={(e) => update(i, { opensAt: e.target.value })}
                    aria-invalid={invalid || undefined}
                    className="w-32"
                  />
                  <span aria-hidden>–</span>
                  <Input
                    type="time"
                    aria-label={t("closesAt", { day: dayName })}
                    value={d.closesAt ?? ""}
                    onChange={(e) => update(i, { closesAt: e.target.value })}
                    aria-invalid={invalid || undefined}
                    className="w-32"
                  />
                </span>
              ) : (
                <span className="text-sm text-muted-foreground">{t("closedAllDay")}</span>
              )}
              {invalid && (
                <span role="alert" className="text-sm text-destructive">
                  {t("errors.HOURS_INVALID")}
                </span>
              )}
            </li>
          );
        })}
      </ul>
      {neverOpen && (
        <p role="alert" className="text-sm text-destructive">
          {t("errors.NEVER_OPEN")}
        </p>
      )}
      <div className="flex gap-2 border-t pt-3">
        <Button
          disabled={!dirty || !!badDay || neverOpen || save.isPending}
          onClick={() => save.mutate(week)}
        >
          {save.isPending ? t("saving") : t("saveHours")}
        </Button>
        {dirty && (
          <Button variant="ghost" onClick={() => setDraft(null)}>
            {t("undo")}
          </Button>
        )}
      </div>
    </section>
  );
}

/** Holidays for one year, as 12 small months and as a list. */
function Holidays({ today }: { today: string }) {
  const t = useTranslations("AdminHours");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const locale = useLocale();
  const errorMessage = useAuthErrorMessage();
  const [year, setYear] = useState(() => Number(today.slice(0, 4)));
  // undefined = closed; { date } = new on that day; a holiday = editing it
  const [editing, setEditing] = useState<Holiday | { date: string } | undefined>(undefined);

  const hours = useQuery({
    queryKey: HOURS_KEY,
    queryFn: () => api.get<{ week: Day[] }>("/admin/hours"),
  });
  const query = useQuery({
    queryKey: [...HOLIDAYS_KEY, year],
    queryFn: () => api.get<Holiday[]>("/admin/holidays", { query: { year } }),
  });

  const holidays = query.data ?? [];
  const byDate = new Map(holidays.map((h) => [h.date, h]));
  const closedWeekdays = new Set(
    (hours.data?.week ?? []).filter((d) => !d.opensAt).map((d) => DAYS.indexOf(d.day)),
  );
  const name = (h: Holiday) => (locale === "si" ? h.nameSi : locale === "ta" ? h.nameTa : h.nameEn);

  return (
    <section aria-labelledby="holidays" className="grid gap-4 rounded-lg border bg-card p-5">
      <div className="flex flex-wrap items-center gap-3">
        <h2 id="holidays" className="font-semibold">
          {t("holidaysTitle")}
        </h2>
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="icon"
            aria-label={t("previousYear")}
            onClick={() => setYear(year - 1)}
          >
            <ChevronLeft aria-hidden />
          </Button>
          <span className="w-14 text-center font-medium tabular-nums" aria-live="polite">
            {year}
          </span>
          <Button
            variant="outline"
            size="icon"
            aria-label={t("nextYear")}
            onClick={() => setYear(year + 1)}
          >
            <ChevronRight aria-hidden />
          </Button>
        </div>
        <Button size="sm" className="ml-auto" onClick={() => setEditing({ date: today })}>
          <Plus aria-hidden />
          {t("addHoliday")}
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">{t("holidaysHint")}</p>

      {query.isError && <FormAlert>{errorMessage(query.error)}</FormAlert>}

      <div className="grid gap-6 lg:grid-cols-[1fr_18rem]">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 12 }, (_, m) => (
            <MiniMonth
              key={m}
              first={`${year}-${String(m + 1).padStart(2, "0")}-01`}
              today={today}
              byDate={byDate}
              closedWeekdays={closedWeekdays}
              name={name}
              onPick={(date) => {
                const h = byDate.get(date);
                if (h) setEditing(h);
                else if (date >= today) setEditing({ date });
              }}
            />
          ))}
        </div>
        <div className="grid content-start gap-2">
          <h3 className="text-sm font-semibold">{t("listTitle", { year })}</h3>
          {query.isPending && <HexLoader label={tCommon("loading")} />}
          {query.isSuccess && holidays.length === 0 && (
            <p className="text-sm text-muted-foreground">{t("noHolidays")}</p>
          )}
          <ul className="grid gap-1 text-sm">
            {holidays.map((h) => (
              <li key={h.id}>
                <button
                  type="button"
                  onClick={() => setEditing(h)}
                  className="flex w-full items-baseline gap-2 rounded px-1 py-0.5 text-left hover:bg-muted"
                >
                  <span className="w-20 shrink-0 text-muted-foreground tabular-nums">
                    {format.dateTime(dayAsDate(h.date), { day: "numeric", month: "short" })}
                  </span>
                  <span>{name(h)}</span>
                </button>
              </li>
            ))}
          </ul>
          <Legend />
        </div>
      </div>

      <Dialog open={editing !== undefined} onOpenChange={(open) => !open && setEditing(undefined)}>
        <DialogContent>
          {editing && (
            <HolidayForm
              key={"id" in editing ? editing.id : editing.date}
              holiday={"id" in editing ? editing : undefined}
              date={editing.date}
              today={today}
              onDone={() => setEditing(undefined)}
            />
          )}
        </DialogContent>
      </Dialog>
    </section>
  );
}

function MiniMonth({
  first,
  today,
  byDate,
  closedWeekdays,
  name,
  onPick,
}: {
  first: string;
  today: string;
  byDate: Map<string, Holiday>;
  closedWeekdays: Set<number>;
  name: (h: Holiday) => string;
  onPick: (date: string) => void;
}) {
  const format = useFormatter();
  const t = useTranslations("AdminHours");
  const month = first.slice(0, 7);
  const offset = weekdayIndex(first);
  const days: string[] = [];
  for (let d = first; d.startsWith(month); d = addDays(d, 1)) days.push(d);

  return (
    <div>
      <h3 className="mb-1 text-sm font-medium">
        {format.dateTime(dayAsDate(first), { month: "long" })}
      </h3>
      <div className="grid grid-cols-7 gap-0.5 text-center text-xs">
        {Array.from({ length: 7 }, (_, i) => (
          <span key={i} aria-hidden className="text-muted-foreground">
            {format.dateTime(dayAsDate(addDays(A_MONDAY, i)), { weekday: "narrow" })}
          </span>
        ))}
        {Array.from({ length: offset }, (_, i) => (
          <span key={`gap-${i}`} aria-hidden />
        ))}
        {days.map((day) => {
          const holiday = byDate.get(day);
          const weeklyClosed = closedWeekdays.has(weekdayIndex(day));
          const past = day < today;
          const clickable = holiday !== undefined || !past;
          const label = format.dateTime(dayAsDate(day), { dateStyle: "long" });
          return (
            <button
              key={day}
              type="button"
              disabled={!clickable}
              onClick={() => onPick(day)}
              title={holiday ? name(holiday) : undefined}
              aria-label={
                holiday
                  ? t("dayHoliday", { date: label, name: name(holiday) })
                  : weeklyClosed
                    ? t("dayClosed", { date: label })
                    : label
              }
              className={cn(
                "aspect-square rounded tabular-nums",
                clickable && "hover:ring-2 hover:ring-primary/50",
                past && !holiday && "text-muted-foreground/50",
                weeklyClosed && !holiday && "bg-muted",
                holiday && "bg-destructive/15 font-semibold text-destructive",
                day === today && "ring-2 ring-primary",
              )}
            >
              {Number(day.slice(8))}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function Legend() {
  const t = useTranslations("AdminHours");
  return (
    <ul className="mt-3 grid gap-1 border-t pt-3 text-xs text-muted-foreground">
      <li className="flex items-center gap-2">
        <span aria-hidden className="size-3 rounded bg-destructive/15" />
        {t("legendHoliday")}
      </li>
      <li className="flex items-center gap-2">
        <span aria-hidden className="size-3 rounded bg-muted" />
        {t("legendClosed")}
      </li>
      <li className="flex items-center gap-2">
        <span aria-hidden className="size-3 rounded ring-2 ring-primary" />
        {t("legendToday")}
      </li>
    </ul>
  );
}

function HolidayForm({
  holiday,
  date,
  today,
  onDone,
}: {
  holiday?: Holiday;
  date: string;
  today: string;
  onDone: () => void;
}) {
  const t = useTranslations("AdminHours");
  const errorText = useErrorMessage("AdminHours");
  const queryClient = useQueryClient();
  const [values, setValues] = useState({
    date,
    nameEn: holiday?.nameEn ?? "",
    nameSi: holiday?.nameSi ?? "",
    nameTa: holiday?.nameTa ?? "",
  });
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const done = (message: string) => {
    void queryClient.invalidateQueries({ queryKey: HOLIDAYS_KEY });
    void queryClient.invalidateQueries({ queryKey: ["library", "hours"] });
    toast.success(message);
    onDone();
  };
  const save = useMutation({
    mutationFn: () => {
      const body = {
        date: values.date,
        nameEn: values.nameEn.trim(),
        nameSi: values.nameSi.trim(),
        nameTa: values.nameTa.trim(),
      };
      return holiday
        ? api.put<Holiday>(`/admin/holidays/${holiday.id}`, body)
        : api.post<Holiday>("/admin/holidays", body);
    },
    onSuccess: () => done(holiday ? t("holidayUpdated") : t("holidayAdded")),
    onError: (e) => setError(errorText(e)),
  });
  const remove = useMutation({
    mutationFn: () => api.delete(`/admin/holidays/${holiday!.id}`),
    onSuccess: () => done(t("holidayDeleted")),
    onError: (e) => setError(errorText(e)),
  });

  const missing =
    !values.date || !values.nameEn.trim() || !values.nameSi.trim() || !values.nameTa.trim();
  const past = values.date < today;
  // An existing holiday in the past can be renamed or deleted, but not moved into the past
  const datePastError = past && (!holiday || values.date !== holiday.date);

  return (
    <form
      className="grid gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        setError(null);
        save.mutate();
      }}
    >
      <DialogHeader>
        <DialogTitle>{holiday ? t("editHoliday") : t("addHoliday")}</DialogTitle>
        <DialogDescription>{t("holidayFormHint")}</DialogDescription>
      </DialogHeader>
      {error && <FormAlert>{error}</FormAlert>}
      <div className="grid gap-1.5">
        <Label htmlFor="holiday-date">{t("date")}</Label>
        <Input
          id="holiday-date"
          type="date"
          min={holiday ? undefined : today}
          value={values.date}
          onChange={(e) => setValues({ ...values, date: e.target.value })}
          aria-invalid={datePastError || undefined}
        />
        {datePastError && <p className="text-sm text-destructive">{t("errors.HOLIDAY_IN_PAST")}</p>}
      </div>
      {(["nameEn", "nameSi", "nameTa"] as const).map((key) => (
        <div key={key} className="grid gap-1.5">
          <Label htmlFor={`holiday-${key}`}>{t(`names.${key}`)}</Label>
          <Input
            id={`holiday-${key}`}
            value={values[key]}
            maxLength={120}
            onChange={(e) => setValues({ ...values, [key]: e.target.value })}
            required
          />
        </div>
      ))}
      <DialogFooter className="sm:justify-between">
        {holiday ? (
          confirmDelete ? (
            <Button
              type="button"
              variant="destructive"
              disabled={remove.isPending}
              onClick={() => remove.mutate()}
            >
              {t("confirmDelete")}
            </Button>
          ) : (
            <Button type="button" variant="ghost" onClick={() => setConfirmDelete(true)}>
              <Trash2 aria-hidden />
              {t("delete")}
            </Button>
          )
        ) : (
          <span />
        )}
        <div className="flex gap-2">
          <Button type="button" variant="outline" onClick={onDone}>
            {t("cancel")}
          </Button>
          <Button type="submit" disabled={missing || datePastError || save.isPending}>
            {save.isPending ? t("saving") : t("saveHoliday")}
          </Button>
        </div>
      </DialogFooter>
    </form>
  );
}
