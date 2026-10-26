"use client";

import { useQuery } from "@tanstack/react-query";
import { ExternalLink, Mail, MapPin, Navigation, Phone } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { OpenNowBadge } from "@/features/library/open-now-badge";
import { libraryWeekday, useHoursFormat, useLibraryHours } from "@/features/library/hours";
import { api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type LibraryInfo = Schema<"LibraryInfoResponse">;

/** The visit page's content (checklist step 206): hours, holidays, contact and map. */
export function VisitDetails() {
  const t = useTranslations("Library");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const locale = useLocale();
  const { time, dayName } = useHoursFormat();
  const hours = useLibraryHours();
  const info = useQuery({
    queryKey: ["library", "info"],
    queryFn: () => api.get<LibraryInfo>("/library/info"),
    staleTime: 60 * 60_000,
  });
  const today = libraryWeekday();

  return (
    <div className="grid gap-8 lg:grid-cols-2">
      <div className="grid content-start gap-8">
        <section aria-labelledby="hours" className="grid gap-3">
          <h2 id="hours" className="text-xl font-semibold">
            {t("hours")}
          </h2>
          <OpenNowBadge />
          {hours.isPending && <HexLoader label={tCommon("loading")} />}
          {hours.data && (
            <table className="w-full max-w-md text-sm">
              <tbody>
                {hours.data.week.map((day) => (
                  <tr
                    key={day.day}
                    className={cn(
                      "border-b last:border-0",
                      day.day === today && "bg-primary/10 font-medium",
                    )}
                    aria-current={day.day === today ? "date" : undefined}
                  >
                    <th scope="row" className="py-2 pl-2 text-left font-normal">
                      {dayName(day.day)}
                      {day.day === today && <span className="sr-only"> ({t("todayRow")})</span>}
                    </th>
                    <td className="py-2 pr-2 text-right tabular-nums">
                      {day.opensAt && day.closesAt
                        ? `${time(day.opensAt)} – ${time(day.closesAt)}`
                        : t("closedAllDay")}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section aria-labelledby="holidays" className="grid gap-2">
          <h2 id="holidays" className="text-lg font-semibold">
            {t("holidays")}
          </h2>
          {hours.data && hours.data.upcomingHolidays.length === 0 && (
            <p className="text-sm text-muted-foreground">{t("noHolidays")}</p>
          )}
          <ul className="grid gap-1 text-sm">
            {hours.data?.upcomingHolidays.map((holiday) => (
              <li
                key={holiday.date}
                className="flex justify-between gap-4 border-b py-1.5 last:border-0"
              >
                <span>
                  {locale === "si"
                    ? holiday.nameSi
                    : locale === "ta"
                      ? holiday.nameTa
                      : holiday.nameEn}
                </span>
                <time dateTime={holiday.date} className="text-muted-foreground">
                  {format.dateTime(new Date(`${holiday.date}T00:00:00Z`), {
                    dateStyle: "medium",
                    timeZone: "UTC",
                  })}
                </time>
              </li>
            ))}
          </ul>
        </section>

        {info.data && (
          <section aria-labelledby="contact" className="grid gap-3">
            <h2 id="contact" className="text-xl font-semibold">
              {t("contact")}
            </h2>
            <address className="grid gap-2 text-sm not-italic">
              <p className="flex items-start gap-2">
                <MapPin aria-hidden className="mt-0.5 size-4 shrink-0 text-primary" />
                <span>
                  <span className="sr-only">{t("address")}: </span>
                  {info.data.name}
                  <br />
                  {info.data.addressLine}, {info.data.city} {info.data.postalCode}
                </span>
              </p>
              <p className="flex items-center gap-2">
                <Phone aria-hidden className="size-4 text-primary" />
                <span className="sr-only">{t("phone")}: </span>
                <a
                  href={`tel:${info.data.phone.replace(/\s/g, "")}`}
                  className="underline-offset-4 hover:underline"
                >
                  {info.data.phone}
                </a>
              </p>
              <p className="flex items-center gap-2">
                <Mail aria-hidden className="size-4 text-primary" />
                <span className="sr-only">{t("email")}: </span>
                <a
                  href={`mailto:${info.data.email}`}
                  className="underline-offset-4 hover:underline"
                >
                  {info.data.email}
                </a>
              </p>
            </address>
          </section>
        )}
      </div>

      {info.data && <LibraryMap info={info.data} />}
    </div>
  );
}

/** OpenStreetMap embed: no API key or account, no tracking cookies. */
function LibraryMap({ info }: { info: LibraryInfo }) {
  const t = useTranslations("Library");
  const { latitude: lat, longitude: lon } = info;
  const bbox = [lon - 0.006, lat - 0.004, lon + 0.006, lat + 0.004]
    .map((n) => n.toFixed(5))
    .join(",");
  return (
    <section aria-labelledby="map" className="grid content-start gap-3">
      <h2 id="map" className="sr-only">
        {t("map")}
      </h2>
      <iframe
        title={t("mapTitle")}
        src={`https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik&marker=${lat},${lon}`}
        className="aspect-[4/3] w-full rounded-lg border"
        loading="lazy"
        referrerPolicy="no-referrer"
      />
      <div className="flex flex-wrap gap-4 text-sm">
        <a
          href={`https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=17/${lat}/${lon}`}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-1 underline-offset-4 hover:underline"
        >
          <ExternalLink aria-hidden className="size-4" />
          {t("openInMaps")}
        </a>
        <a
          href={`https://www.google.com/maps/dir/?api=1&destination=${lat},${lon}`}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-1 underline-offset-4 hover:underline"
        >
          <Navigation aria-hidden className="size-4" />
          {t("directions")}
        </a>
      </div>
    </section>
  );
}
