import "server-only";
import { cacheLife, cacheTag } from "next/cache";
import { API_BASE_URL, type Schema } from "@/lib/api";

export type Book = Schema<"BookResponse">;

/**
 * A book for the public pages, fetched on the server and cached for a few minutes (Cache
 * Components). Title, authors and description rarely change; a staff edit shows up within
 * minutes. Availability is NOT here: it changes all the time and is loaded in the browser.
 *
 * @returns null when the book doesn't exist (the page shows 404)
 */
export async function getPublicBook(id: string): Promise<Book | null> {
  "use cache";
  cacheLife("minutes");
  cacheTag(`book-${id}`);

  // UUIDs only: anything else can't be a book, so don't bother the API
  if (!/^[0-9a-f-]{36}$/i.test(id)) return null;
  const response = await fetch(`${API_BASE_URL}/books/${id}`, {
    headers: { Accept: "application/json" },
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Book ${id}: HTTP ${response.status}`);
  return (await response.json()) as Book;
}
