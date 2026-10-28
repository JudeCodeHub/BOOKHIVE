import "server-only";
import { cacheLife, cacheTag } from "next/cache";
import { API_BASE_URL } from "@/lib/api";
import type { LibraryEvent } from "./library-dates";

/**
 * An event for its public page, fetched on the server and cached for a few minutes (a staff
 * edit shows within minutes). Places left are NOT taken from here: the sign-up box loads them
 * fresh in the browser.
 *
 * @returns null when there's no such (published or cancelled) event: the page shows 404
 */
export async function getPublicEvent(id: string): Promise<LibraryEvent | null> {
  "use cache";
  cacheLife("minutes");
  cacheTag(`event-${id}`);

  if (!/^[0-9a-f-]{36}$/i.test(id)) return null;
  const response = await fetch(`${API_BASE_URL}/events/${id}`, {
    headers: { Accept: "application/json" },
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Event ${id}: HTTP ${response.status}`);
  return (await response.json()) as LibraryEvent;
}
