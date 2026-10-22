import {
  BookOpenText,
  Brain,
  FlaskConical,
  Globe,
  Languages,
  Laptop,
  Palette,
  Sparkles,
  Users,
  Wrench,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";

// The 10 main classes of the Dewey Decimal Classification: how the shelves are numbered.
// The slug becomes /categories/<slug> (PRD §10 "Browse category", built in the search phase).
const SUBJECTS = [
  { ddc: "000", slug: "general", key: "general", icon: Laptop },
  { ddc: "100", slug: "philosophy", key: "philosophy", icon: Brain },
  { ddc: "200", slug: "religion", key: "religion", icon: Sparkles },
  { ddc: "300", slug: "social-sciences", key: "socialSciences", icon: Users },
  { ddc: "400", slug: "language", key: "language", icon: Languages },
  { ddc: "500", slug: "science", key: "science", icon: FlaskConical },
  { ddc: "600", slug: "technology", key: "technology", icon: Wrench },
  { ddc: "700", slug: "arts", key: "arts", icon: Palette },
  { ddc: "800", slug: "literature", key: "literature", icon: BookOpenText },
  { ddc: "900", slug: "history", key: "history", icon: Globe },
] as const;

export function SubjectGrid() {
  const t = useTranslations("Home");

  return (
    <section aria-labelledby="subjects-title" className="mx-auto w-full max-w-6xl px-4 py-14">
      <h2 id="subjects-title" className="text-2xl font-semibold sm:text-3xl">
        {t("subjectsTitle")}
      </h2>
      <p className="mt-1 text-muted-foreground">{t("subjectsSubtitle")}</p>

      <ul className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {SUBJECTS.map(({ ddc, slug, key, icon: Icon }) => (
          <li key={ddc}>
            <Link
              href={`/categories/${slug}`}
              className="group flex h-full flex-col gap-3 rounded-xl border bg-card p-4 transition-colors hover:border-primary hover:bg-accent"
            >
              <span className="flex items-center justify-between">
                <Icon aria-hidden className="size-6 text-primary" />
                <span className="font-mono text-xs text-muted-foreground">{ddc}</span>
              </span>
              <span className="font-medium group-hover:text-accent-foreground">
                {t(`subjects.${key}`)}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
