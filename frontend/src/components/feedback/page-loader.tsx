import { getTranslations } from "next-intl/server";
import { HexLoader } from "@/components/brand/hex-loader";

/** Shown by every area's loading.tsx while a page's data is loading. */
export async function PageLoader() {
  const t = await getTranslations("Common");

  return (
    <div className="flex flex-1 items-center justify-center p-16">
      <HexLoader label={t("loading")} />
    </div>
  );
}
