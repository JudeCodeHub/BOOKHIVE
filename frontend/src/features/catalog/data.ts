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

export type CategoryNode = Schema<"CategoryTreeResponse">;
export type PublicAuthor = Schema<"PublicAuthorResponse">;

/** The whole category tree (it rarely changes), for category pages and breadcrumbs. */
export async function getCategoryTree(): Promise<CategoryNode[]> {
  "use cache";
  cacheLife("hours");
  cacheTag("categories");

  const response = await fetch(`${API_BASE_URL}/categories`, {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error(`Categories: HTTP ${response.status}`);
  return (await response.json()) as CategoryNode[];
}

/**
 * A category and the path to it from the top ([Literature, Sinhala literature]), or null if the
 * id isn't in the tree.
 */
export async function getCategoryPath(id: string): Promise<CategoryNode[] | null> {
  const find = (nodes: CategoryNode[], path: CategoryNode[]): CategoryNode[] | null => {
    for (const node of nodes) {
      const here = [...path, node];
      if (node.id === id) return here;
      const below = find(node.children, here);
      if (below) return below;
    }
    return null;
  };
  return find(await getCategoryTree(), []);
}

/** An author for their public page; null when there's no such author. */
export async function getPublicAuthor(id: string): Promise<PublicAuthor | null> {
  "use cache";
  cacheLife("minutes");
  cacheTag(`author-${id}`);

  if (!/^[0-9a-f-]{36}$/i.test(id)) return null;
  const response = await fetch(`${API_BASE_URL}/authors/${id}`, {
    headers: { Accept: "application/json" },
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Author ${id}: HTTP ${response.status}`);
  return (await response.json()) as PublicAuthor;
}
