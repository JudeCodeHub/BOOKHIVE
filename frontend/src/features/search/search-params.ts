export type SearchSort = "RELEVANCE" | "NEWEST" | "TITLE" | "YEAR";
export type BookFormat = "BOOK" | "MAGAZINE" | "NEWSPAPER" | "DVD" | "AUDIOBOOK";

/** Everything the search page shows, read from (and written to) the URL. */
export type SearchState = {
  q: string;
  language: string;
  format: string;
  category: string;
  available: boolean;
  yearFrom: string;
  yearTo: string;
  sort: SearchSort;
  page: number;
  view: "grid" | "list";
};

const SORTS: SearchSort[] = ["RELEVANCE", "NEWEST", "TITLE", "YEAR"];

/** The URL is the source of truth: shareable, bookmarkable, and the back button works. */
export function readSearch(params: URLSearchParams): SearchState {
  const sort = params.get("sort") as SearchSort | null;
  return {
    q: params.get("q") ?? "",
    language: params.get("language") ?? "",
    format: params.get("format") ?? "",
    category: params.get("category") ?? "",
    available: params.get("available") === "true",
    yearFrom: params.get("yearFrom") ?? "",
    yearTo: params.get("yearTo") ?? "",
    sort: sort && SORTS.includes(sort) ? sort : "RELEVANCE",
    page: Math.max(0, Number(params.get("page") ?? 0) || 0),
    view: params.get("view") === "list" ? "list" : "grid",
  };
}

/** A query string with only the values that differ from the defaults (short, clean URLs). */
export function writeSearch(state: SearchState): string {
  const params = new URLSearchParams();
  if (state.q) params.set("q", state.q);
  if (state.language) params.set("language", state.language);
  if (state.format) params.set("format", state.format);
  if (state.category) params.set("category", state.category);
  if (state.available) params.set("available", "true");
  if (state.yearFrom) params.set("yearFrom", state.yearFrom);
  if (state.yearTo) params.set("yearTo", state.yearTo);
  if (state.sort !== "RELEVANCE") params.set("sort", state.sort);
  if (state.page > 0) params.set("page", String(state.page));
  if (state.view !== "grid") params.set("view", state.view);
  return params.toString();
}
