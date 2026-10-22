import { FeatureList } from "@/features/home/feature-list";
import { HeroSearch } from "@/features/home/hero-search";
import { JoinCta } from "@/features/home/join-cta";
import { SubjectGrid } from "@/features/home/subject-grid";

// New arrivals, popular books, events and news join this page once their backend
// endpoints exist (catalog, search and events phases).
export default function Home() {
  return (
    <>
      <HeroSearch />
      <SubjectGrid />
      <FeatureList />
      <JoinCta />
    </>
  );
}
