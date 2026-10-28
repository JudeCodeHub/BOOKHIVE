"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/features/auth/auth-card";
import { Link } from "@/i18n/navigation";
import { api } from "@/lib/api";
import { cn } from "@/lib/utils";
import { EventCard } from "./event-card";
import {
  addDays,
  dayAsDate,
  dayStart,
  libraryDay,
  weekdayIndex,
  type LibraryEvent,
} from "./library-dates";

/**
 * Month calendar of events (checklist step 341). `month` is "2026-11". The grid is whole weeks
 * (Monday first), so it also shows the edges of the months before and after. On phones the days
 * only get dots and the month's events are listed under the grid.
 */
export function EventCalendar({
  month,
  onMonthChange,
}: {
  month: string;
  onMonthChange: (month: string) => void;
}) {
  const t = useTranslations("Events");
  const tCommon = useTranslations("Common");
  const format = useFormatter();

  const first = `${month}-01`;
  const gridStart = addDays(first, -weekdayIndex(first));
  const nextMonth = addDays(first, 32).slice(0, 7);
  const prevMonth = addDays(first, -1).slice(0, 7);
  const lastOfMonth = addDays(`${nextMonth}-01`, -1);
  const gridEnd = addDays(lastOfMonth, 6 - weekdayIndex(lastOfMonth));
  const days: string[] = [];
  for (let day = gridStart; day <= gridEnd; day = addDays(day, 1)) days.push(day);

  const events = useQuery({
    queryKey: ["events", "calendar", month],
    queryFn: () =>
      api.get<LibraryEvent[]>("/events/calendar", {
        query: {
          from: dayStart(gridStart).toISOString(),
          to: dayStart(addDays(gridEnd, 1)).toISOString(),
        },
      }),
    placeholderData: keepPreviousData,
  });

  const byDay = new Map<string, LibraryEvent[]>();
  for (const event of events.data ?? []) {
    const day = libraryDay(event.startsAt);
    byDay.set(day, [...(byDay.get(day) ?? []), event]);
  }
  const today = libraryDay(new Date());
  const inMonth = (events.data ?? []).filter((e) => libraryDay(e.startsAt).startsWith(month));

  return (
    <div className="grid gap-4">
      <div className="flex items-center justify-between gap-2">
        <Button
          variant="outline"
          size="icon"
          onClick={() => onMonthChange(prevMonth)}
          aria-label={t("previousMonth")}
        >
          <ChevronLeft aria-hidden />
        </Button>
        <h2 className="font-heading text-xl font-semibold" aria-live="polite">
          {format.dateTime(dayAsDate(first), { month: "long", year: "numeric" })}
        </h2>
        <Button
          variant="outline"
          size="icon"
          onClick={() => onMonthChange(nextMonth)}
          aria-label={t("nextMonth")}
        >
          <ChevronRight aria-hidden />
        </Button>
      </div>

      {events.isError && <FormAlert>{t("loadError")}</FormAlert>}

      <div className="relative">
        {events.isPending && (
          <div className="absolute inset-0 z-10 flex items-center justify-center bg-background/60">
            <HexLoader label={tCommon("loading")} />
          </div>
        )}
        {/* A real table: screen readers announce the weekday and day of each cell */}
        <table className="w-full table-fixed border-collapse text-sm">
          <thead>
            <tr>
              {days.slice(0, 7).map((day) => (
                <th key={day} scope="col" className="pb-2 font-medium text-muted-foreground">
                  <abbr
                    title={format.dateTime(dayAsDate(day), { weekday: "long" })}
                    className="no-underline"
                  >
                    {format.dateTime(dayAsDate(day), { weekday: "short" })}
                  </abbr>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: days.length / 7 }, (_, week) => (
              <tr key={week}>
                {days.slice(week * 7, week * 7 + 7).map((day) => {
                  const dayEvents = byDay.get(day) ?? [];
                  return (
                    <td
                      key={day}
                      aria-current={day === today ? "date" : undefined}
                      className={cn(
                        "h-16 border align-top sm:h-28",
                        !day.startsWith(month) && "bg-muted/40 text-muted-foreground",
                      )}
                    >
                      <div className="grid gap-1 p-1">
                        <span
                          className={cn(
                            "flex size-6 items-center justify-center rounded-full text-xs tabular-nums",
                            day === today && "bg-primary font-semibold text-primary-foreground",
                          )}
                        >
                          {Number(day.slice(8))}
                        </span>
                        {dayEvents.length > 0 && (
                          <span className="sr-only">
                            {t("eventsOnDay", { count: dayEvents.length })}
                          </span>
                        )}
                        {/* Phones: one dot per event */}
                        <span aria-hidden className="flex flex-wrap gap-0.5 sm:hidden">
                          {dayEvents.map((e) => (
                            <span key={e.id} className="size-1.5 rounded-full bg-primary" />
                          ))}
                        </span>
                        {/* Wider screens: the titles, as links */}
                        <ul className="hidden gap-1 sm:grid">
                          {dayEvents.map((e) => (
                            <li key={e.id}>
                              <Link
                                href={`/events/${e.id}`}
                                className="block truncate rounded bg-primary/10 px-1 py-0.5 text-xs text-primary hover:bg-primary/20"
                                title={e.title}
                              >
                                <span className="tabular-nums">
                                  {format.dateTime(new Date(e.startsAt), {
                                    hour: "numeric",
                                    minute: "2-digit",
                                  })}
                                </span>{" "}
                                {e.title}
                              </Link>
                            </li>
                          ))}
                        </ul>
                      </div>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Phones: the month's events as a list, since the grid only has dots */}
      <section aria-label={t("thisMonth")} className="grid gap-3 sm:hidden">
        {events.isSuccess && inMonth.length === 0 && (
          <p className="text-center text-muted-foreground">{t("noneThisMonth")}</p>
        )}
        {inMonth.map((event) => (
          <EventCard key={event.id} event={event} />
        ))}
      </section>
      {events.isSuccess && inMonth.length === 0 && (
        <p className="hidden text-center text-muted-foreground sm:block">{t("noneThisMonth")}</p>
      )}
    </div>
  );
}
