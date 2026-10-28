import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { HexLoader } from "@/components/brand/hex-loader";
import { EventsPage } from "@/features/events/events-page";

// Checked in development for instant navigation (next.config: manual-warning)
export const instant = true;

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Events");
  return { title: t("metaTitle"), description: t("intro") };
}

/** Events list + calendar (checklist step 341); the view and month are read from the URL. */
export default async function Page() {
  const t = await getTranslations("Common");
  return (
    <Suspense
      fallback={
        <div className="flex flex-1 justify-center py-16">
          <HexLoader label={t("loading")} />
        </div>
      }
    >
      <EventsPage />
    </Suspense>
  );
}
