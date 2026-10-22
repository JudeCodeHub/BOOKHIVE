import { notFound } from "next/navigation";

// This page only exists to 404, so skip Next.js's dev-time "instant navigation" check,
// which would otherwise warn that the page can't render.
export const instant = false;

// Any URL that matches no page (e.g. /en/nothing-here) lands here and shows the
// translated [locale]/not-found.tsx instead of Next.js's default English 404.
export default function CatchAllPage() {
  notFound();
}
