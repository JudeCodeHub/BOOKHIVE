import { Suspense } from "react";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { StocktakeSession } from "@/features/stocktake/stocktake-session";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Stocktake");
  return { title: t("title") };
}

/** The id is read in the browser (useParams). */
export default function Page() {
  return (
    <Suspense>
      <StocktakeSession />
    </Suspense>
  );
}
