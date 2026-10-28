import { useTranslations } from "next-intl";
import { BookShelf } from "@/features/home/book-shelf";
import { FeatureList } from "@/features/home/feature-list";
import { HeroSearch } from "@/features/home/hero-search";
import { JoinCta } from "@/features/home/join-cta";
import { SubjectGrid } from "@/features/home/subject-grid";
import { WhatsOn } from "@/features/home/whats-on";

// Checked in development for instant navigation (next.config: manual-warning)
export const instant = true;

/**
 * Home page (checklist steps 202, 352): search first, then what's new and popular, what's on
 * (events and news), then subjects. The page itself is static; the book rows and "what's on"
 * load in the browser, so availability and places left are always current.
 */
export default function Home() {
  const t = useTranslations("Home");

  return (
    <>
      <HeroSearch />
      <BookShelf
        id="new-arrivals"
        title={t("newArrivals")}
        subtitle={t("newArrivalsSubtitle")}
        endpoint="/books/new-arrivals"
        seeAllHref="/search?sort=NEWEST"
      />
      <BookShelf
        id="popular"
        title={t("popular")}
        subtitle={t("popularSubtitle")}
        endpoint="/books/popular"
        seeAllHref="/search"
      />
      <WhatsOn />
      <SubjectGrid />
      <FeatureList />
      <JoinCta />
    </>
  );
}
