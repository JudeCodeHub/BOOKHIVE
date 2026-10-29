"use client";

import { useState } from "react";
import { CalendarRange, Check } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { addDays, dayAsDate } from "@/features/events/library-dates";
import { cn } from "@/lib/utils";

type PresetId =
  "last7" | "last30" | "last90" | "thisMonth" | "lastMonth" | "thisYear" | "last12Months";
type Preset = { id: PresetId; from: string; to: string };

/** "2026-10-29" -> "2026-10-01" */
const monthStart = (day: string) => `${day.slice(0, 7)}-01`;

/** Ranges people reach for, newest first; days are Sri Lankan library days. */
export function rangePresets(today: string): Preset[] {
  const thisMonth = monthStart(today);
  const lastMonthEnd = addDays(thisMonth, -1);
  return [
    { id: "last7", from: addDays(today, -6), to: today },
    { id: "last30", from: addDays(today, -29), to: today },
    { id: "last90", from: addDays(today, -89), to: today },
    { id: "thisMonth", from: thisMonth, to: today },
    { id: "lastMonth", from: monthStart(lastMonthEnd), to: lastMonthEnd },
    { id: "thisYear", from: `${today.slice(0, 4)}-01-01`, to: today },
    { id: "last12Months", from: monthStart(addDays(today, -365)), to: today },
  ];
}

/**
 * The reports' date range (checklist step 374): presets as rows (with a check on the one in use),
 * a custom range under a line at the bottom. Dates can't be in the future or the wrong way round.
 */
export function DateRangePicker({
  from,
  to,
  today,
  onChange,
}: {
  from: string;
  to: string;
  today: string;
  onChange: (from: string, to: string) => void;
}) {
  const t = useTranslations("Reports.picker");
  const format = useFormatter();
  const [open, setOpen] = useState(false);
  const [custom, setCustom] = useState({ from, to });
  const presets = rangePresets(today);
  const current = presets.find((p) => p.from === from && p.to === to);
  const label = (d: string) => format.dateTime(dayAsDate(d), { dateStyle: "medium" });
  const customOk = custom.from && custom.to && custom.from <= custom.to && custom.to <= today;

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) setCustom({ from, to });
      }}
    >
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" aria-label={t("change")}>
          <CalendarRange aria-hidden />
          {current ? t(`presets.${current.id}`) : `${label(from)} – ${label(to)}`}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="grid gap-1">
        <ul className="grid">
          {presets.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                onClick={() => {
                  onChange(p.from, p.to);
                  setOpen(false);
                }}
                className={cn(
                  "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-muted",
                  current?.id === p.id && "font-semibold",
                )}
              >
                <Check
                  aria-hidden
                  className={cn("size-4", current?.id === p.id ? "opacity-100" : "opacity-0")}
                />
                <span className="flex-1">{t(`presets.${p.id}`)}</span>
                <span className="text-xs font-normal text-muted-foreground">
                  {format.dateTime(dayAsDate(p.from), { day: "numeric", month: "short" })}
                </span>
              </button>
            </li>
          ))}
        </ul>
        <form
          className="grid gap-2 border-t px-2 pt-2 pb-1"
          onSubmit={(e) => {
            e.preventDefault();
            if (!customOk) return;
            onChange(custom.from, custom.to);
            setOpen(false);
          }}
        >
          <p className="text-xs font-medium text-muted-foreground">{t("custom")}</p>
          <div className="grid grid-cols-2 gap-2">
            <div className="grid gap-1">
              <Label htmlFor="range-from" className="text-xs">
                {t("from")}
              </Label>
              <Input
                id="range-from"
                type="date"
                max={custom.to || today}
                value={custom.from}
                onChange={(e) => setCustom({ ...custom, from: e.target.value })}
              />
            </div>
            <div className="grid gap-1">
              <Label htmlFor="range-to" className="text-xs">
                {t("to")}
              </Label>
              <Input
                id="range-to"
                type="date"
                min={custom.from || undefined}
                max={today}
                value={custom.to}
                onChange={(e) => setCustom({ ...custom, to: e.target.value })}
              />
            </div>
          </div>
          <Button type="submit" size="sm" disabled={!customOk}>
            {t("apply")}
          </Button>
        </form>
      </PopoverContent>
    </Popover>
  );
}

/** The weeding list's single day: "not borrowed since…". */
export function SincePicker({
  since,
  today,
  onChange,
}: {
  since: string;
  today: string;
  onChange: (since: string) => void;
}) {
  const t = useTranslations("Reports.picker");
  const format = useFormatter();
  const [open, setOpen] = useState(false);
  const [custom, setCustom] = useState(since);
  const back = (months: number) => {
    const d = new Date(`${today}T00:00:00Z`);
    d.setUTCMonth(d.getUTCMonth() - months);
    return d.toISOString().slice(0, 10);
  };
  const presets = [
    { id: "months6", day: back(6) },
    { id: "year1", day: back(12) },
    { id: "years2", day: back(24) },
    { id: "years3", day: back(36) },
  ] as const;
  const current = presets.find((p) => p.day === since);

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) setCustom(since);
      }}
    >
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm" aria-label={t("changeSince")}>
          <CalendarRange aria-hidden />
          {t("since", {
            date: current
              ? t(`ago.${current.id}`)
              : format.dateTime(dayAsDate(since), { dateStyle: "medium" }),
          })}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="grid gap-1">
        <ul className="grid">
          {presets.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                onClick={() => {
                  onChange(p.day);
                  setOpen(false);
                }}
                className={cn(
                  "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-muted",
                  current?.id === p.id && "font-semibold",
                )}
              >
                <Check
                  aria-hidden
                  className={cn("size-4", current?.id === p.id ? "opacity-100" : "opacity-0")}
                />
                {t(`ago.${p.id}`)}
              </button>
            </li>
          ))}
        </ul>
        <form
          className="grid gap-2 border-t px-2 pt-2 pb-1"
          onSubmit={(e) => {
            e.preventDefault();
            if (custom && custom < today) {
              onChange(custom);
              setOpen(false);
            }
          }}
        >
          <Label htmlFor="since-day" className="text-xs">
            {t("customDay")}
          </Label>
          <Input
            id="since-day"
            type="date"
            max={addDays(today, -1)}
            value={custom}
            onChange={(e) => setCustom(e.target.value)}
          />
          <Button type="submit" size="sm" disabled={!custom || custom >= today}>
            {t("apply")}
          </Button>
        </form>
      </PopoverContent>
    </Popover>
  );
}
