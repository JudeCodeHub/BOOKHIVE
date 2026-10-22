import { notFound } from "next/navigation";

// Any URL that matches no page (e.g. /en/nothing-here) lands here and shows the
// translated [locale]/not-found.tsx instead of Next.js's default English 404.
export default function CatchAllPage() {
  notFound();
}
