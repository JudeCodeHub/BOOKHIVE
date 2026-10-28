"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { CalendarDays, CalendarX, List } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Pagination } from "@/components/layout/pagination";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FormAlert } from "@/features/auth/auth-card";
import { usePathname, useRouter } from "@/i18n/navigation";
import { api, type PageResponse } from "@/lib/api";
import { EventCalendar } from "./event-calendar";
import { EventCard } from "./event-card";
import { libraryDay, type LibraryEvent } from "./library-dates";

const PAGE_SIZE = 12;

/**
 * The public events page (checklist step 341): upcoming events as a list, or a month calendar.
 * The view, page and month are in the URL (?view=calendar&month=2026-11), so they can be shared
 * and the back button works.
 */
export function EventsPage() {
  const t = useTranslations("Events");
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const view = params.get("view") === "calendar" ? "calendar" : "list";
  const page = Math.max(0, Number(params.get("page") ?? 1) - 1) || 0;
  const monthParam = params.get("month");
  const month =
    monthParam && /^\d{4}-(0[1-9]|1[0-2])$/.test(monthParam)
      ? monthParam
      : libraryDay(new Date()).slice(0, 7);

  const go = (next: Record<string, string | null>) => {
    const query = new URLSearchParams(params);
    for (const [key, value] of Object.entries(next)) {
      if (value == null) query.delete(key);
      else query.set(key, value);
    }
    const search = query.toString();
    router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
  };

  return (
    <div className="mx-auto grid w-full max-w-6xl gap-6 px-4 py-8">
      <div className="grid gap-1">
        <h1 className="font-heading text-3xl font-semibold">{t("title")}</h1>
        <p className="text-muted-foreground">{t("intro")}</p>
      </div>
      <Tabs
        value={view}
        onValueChange={(value) =>
          go({ view: value === "calendar" ? "calendar" : null, page: null, month: null })
        }
        className="gap-6"
      >
        <TabsList>
          <TabsTrigger value="list">
            <List aria-hidden />
            {t("listView")}
          </TabsTrigger>
          <TabsTrigger value="calendar">
            <CalendarDays aria-hidden />
            {t("calendarView")}
          </TabsTrigger>
        </TabsList>
        <TabsContent value="list">
          <UpcomingList
            page={page}
            onPageChange={(p) => go({ page: p > 0 ? String(p + 1) : null })}
          />
        </TabsContent>
        <TabsContent value="calendar">
          <EventCalendar month={month} onMonthChange={(m) => go({ month: m })} />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function UpcomingList({
  page,
  onPageChange,
}: {
  page: number;
  onPageChange: (page: number) => void;
}) {
  const t = useTranslations("Events");
  const tCommon = useTranslations("Common");
  const events = useQuery({
    queryKey: ["events", "upcoming", page],
    queryFn: () =>
      api.get<PageResponse<LibraryEvent>>("/events", { query: { page, size: PAGE_SIZE } }),
    placeholderData: keepPreviousData,
  });

  if (events.isPending) {
    return (
      <div className="flex justify-center py-16">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (events.isError) return <FormAlert>{t("loadError")}</FormAlert>;
  if (events.data.content.length === 0) {
    return (
      <div className="grid justify-items-center gap-2 py-16 text-center text-muted-foreground">
        <CalendarX aria-hidden className="size-10" />
        <p>{t("noneUpcoming")}</p>
      </div>
    );
  }

  return (
    <div className="grid gap-6">
      <ul className="grid gap-4">
        {events.data.content.map((event) => (
          <li key={event.id}>
            <EventCard event={event} />
          </li>
        ))}
      </ul>
      <Pagination
        page={events.data.page}
        totalPages={events.data.totalPages}
        onChange={(p) => {
          onPageChange(p);
          window.scrollTo({ top: 0, behavior: "smooth" });
        }}
      />
    </div>
  );
}
