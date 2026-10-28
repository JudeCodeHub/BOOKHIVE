import type { Metadata } from "next";
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";
import { MyWishlist } from "@/features/wishlist/my-wishlist";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Wishlist");
  return { title: t("title") };
}

export default function WishlistPage() {
  const t = useTranslations("Wishlist");

  return (
    <div className="grid gap-6">
      <h1 className="text-2xl font-semibold">{t("title")}</h1>
      <MyWishlist />
    </div>
  );
}
