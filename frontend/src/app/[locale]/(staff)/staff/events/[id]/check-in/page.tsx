import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { EventCheckIn } from "@/features/events/event-check-in";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("CheckIn");
  return { title: t("title") };
}

/** The id is read in the browser (useParams). */
export default function Page() {
  return (
    <Suspense>
      <EventCheckIn />
    </Suspense>
  );
}
