import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { HexLoader } from "@/components/brand/hex-loader";
import { SearchPage } from "@/features/search/search-page";

// Checked in development for instant navigation (next.config: manual-warning)
export const instant = true;

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Search");
  return {
    title: t("metaTitle"),
    // Endless filter combinations: not useful as search-engine results themselves
    robots: { index: false, follow: true },
  };
}

/** Search results are read from the URL (?q=...), which is only known in the browser. */
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
      <SearchPage />
    </Suspense>
  );
}
