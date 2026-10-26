"use client";

import { useSearchParams } from "next/navigation";
import { MySuggestions } from "@/features/membership/my-suggestions";

/** The suggestion page, pre-filled from ?title= (an empty search's "Suggest it" link). */
export function SuggestionsWithTitle() {
  const title = useSearchParams().get("title")?.slice(0, 300) ?? undefined;
  return <MySuggestions initialTitle={title} />;
}
