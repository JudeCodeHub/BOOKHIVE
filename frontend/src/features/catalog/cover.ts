import { API_BASE_URL } from "@/lib/api";

export type CoverSize = "small" | "medium" | "large";

/**
 * Public cover URL (served and cached for a year by the API). The ?v= is the cover version, so a
 * new cover gets a new URL. Null when the book has no cover.
 */
export function coverUrl(
  bookId: string,
  version: string | null | undefined,
  size: CoverSize = "small",
): string | null {
  return version ? `${API_BASE_URL}/books/${bookId}/cover/${size}?v=${version}` : null;
}
