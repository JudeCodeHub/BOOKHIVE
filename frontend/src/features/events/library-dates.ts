import { API_BASE_URL, type Schema } from "@/lib/api";

export type LibraryEvent = Schema<"EventSummary">;

/** Sri Lanka has no daylight saving, so the library's day always starts at 00:00 +05:30. */
const LIBRARY_OFFSET_MS = 5.5 * 60 * 60_000;

/** "2026-11-03": the library's calendar day of an instant (event dates are shown in Colombo). */
export function libraryDay(instant: string | Date): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Colombo" }).format(new Date(instant));
}

/** The instant a library day ("2026-11-03") starts. */
export function dayStart(day: string): Date {
  const [y, m, d] = day.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d) - LIBRARY_OFFSET_MS);
}

/** The day `days` after `day` (negative goes back). */
export function addDays(day: string, days: number): string {
  const [y, m, d] = day.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/** 0 = Monday … 6 = Sunday (the calendar's weeks start on Monday). */
export function weekdayIndex(day: string): number {
  const [y, m, d] = day.split("-").map(Number);
  return (new Date(Date.UTC(y, m - 1, d)).getUTCDay() + 6) % 7;
}

/** A Date for showing a library day with the formatter (midday, so no time zone moves it). */
export function dayAsDate(day: string): Date {
  return new Date(dayStart(day).getTime() + 12 * 60 * 60_000);
}

/** The event poster URL (cached by the API; ?v= changes with a new poster), or null. */
export function posterUrl(event: Pick<LibraryEvent, "id" | "imageVersion">): string | null {
  return event.imageVersion
    ? `${API_BASE_URL}/events/${event.id}/image?v=${event.imageVersion}`
    : null;
}

/** The description is sanitised HTML (RichText); lists show it as a short plain-text teaser. */
export function plainText(html: string | null | undefined): string {
  return (html ?? "")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, " ")
    .trim();
}

/** An instant as a datetime-local value in library time: "2026-11-20T10:00". */
export function toLibraryInput(instant: string): string {
  return new Date(new Date(instant).getTime() + LIBRARY_OFFSET_MS).toISOString().slice(0, 16);
}

/** A datetime-local value ("2026-11-20T10:00", library time) as an ISO instant for the API. */
export function fromLibraryInput(value: string): string {
  return new Date(new Date(`${value}:00Z`).getTime() - LIBRARY_OFFSET_MS).toISOString();
}
