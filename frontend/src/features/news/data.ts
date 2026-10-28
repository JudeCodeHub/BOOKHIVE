import "server-only";
import { cacheLife, cacheTag } from "next/cache";
import { API_BASE_URL, type PageResponse, type Schema } from "@/lib/api";

export type NewsSummary = Schema<"NewsSummary">;
export type NewsPost = Schema<"NewsPostResponse">;

export const NEWS_PAGE_SIZE = 10;

/**
 * A page of published news (pinned first, then newest), fetched on the server and cached for a
 * few minutes: a new post shows up within minutes.
 */
export async function getNewsPage(page: number): Promise<PageResponse<NewsSummary>> {
  "use cache";
  cacheLife("minutes");
  cacheTag("news");

  const response = await fetch(`${API_BASE_URL}/news?page=${page}&size=${NEWS_PAGE_SIZE}`, {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error(`News: HTTP ${response.status}`);
  return (await response.json()) as PageResponse<NewsSummary>;
}

/** One published post; null when there's no such (published) post: the page shows 404. */
export async function getNewsPost(id: string): Promise<NewsPost | null> {
  "use cache";
  cacheLife("minutes");
  cacheTag(`news-${id}`);

  if (!/^[0-9a-f-]{36}$/i.test(id)) return null;
  const response = await fetch(`${API_BASE_URL}/news/${id}`, {
    headers: { Accept: "application/json" },
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`News ${id}: HTTP ${response.status}`);
  return (await response.json()) as NewsPost;
}
