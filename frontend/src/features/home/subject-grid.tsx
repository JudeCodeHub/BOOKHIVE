"use client";

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
import { useQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { api, type Schema } from "@/lib/api";

type CategoryNode = Schema<"CategoryTreeResponse">;

// The 10 main classes of the Dewey Decimal Classification: how the shelves are numbered. The
// names come from the library's own category tree when it has that class (so staff renames show
// here), with these built-in names as a fallback while it loads or if a class is missing.
const SUBJECTS = [
  { ddc: "000", key: "general", icon: Laptop },
  { ddc: "100", key: "philosophy", icon: Brain },
  { ddc: "200", key: "religion", icon: Sparkles },
  { ddc: "300", key: "socialSciences", icon: Users },
  { ddc: "400", key: "language", icon: Languages },
  { ddc: "500", key: "science", icon: FlaskConical },
  { ddc: "600", key: "technology", icon: Wrench },
  { ddc: "700", key: "arts", icon: Palette },
  { ddc: "800", key: "literature", icon: BookOpenText },
  { ddc: "900", key: "history", icon: Globe },
] as const;

/**
 * "Browse by subject" (checklist step 202): one tile per Dewey main class, linked to the books in
 * it (its category page).
 */
export function SubjectGrid() {
  const t = useTranslations("Home");
  const locale = useLocale();
  const tree = useQuery({
    queryKey: ["categories"],
    queryFn: () => api.get<CategoryNode[]>("/categories"),
    staleTime: 10 * 60_000,
  });
  const byDdc = new Map((tree.data ?? []).map((node) => [node.ddcCode, node]));
  const name = (node: CategoryNode) =>
    locale === "si" ? node.nameSi : locale === "ta" ? node.nameTa : node.nameEn;

  return (
    <section aria-labelledby="subjects-title" className="mx-auto w-full max-w-6xl px-4 py-14">
      <h2 id="subjects-title" className="text-2xl font-semibold sm:text-3xl">
        {t("subjectsTitle")}
      </h2>
      <p className="mt-1 text-muted-foreground">{t("subjectsSubtitle")}</p>

      <ul className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {SUBJECTS.map(({ ddc, key, icon: Icon }) => {
          const node = byDdc.get(ddc);
          const label = node ? name(node) : t(`subjects.${key}`);
          return (
            <li key={ddc}>
              <Link
                href={node ? `/categories/${node.id}` : "/search"}
                // The card shows the code and the name, and both must be in the link's name (WCAG 2.5.3)
                aria-label={t("subjectBooks", { name: `${ddc} ${label}` })}
                className="group flex h-full flex-col gap-3 rounded-xl border bg-card p-4 transition-colors hover:border-primary hover:bg-accent"
              >
                <span className="flex items-center justify-between">
                  <Icon aria-hidden className="size-6 text-primary" />
                  <span className="font-mono text-xs text-muted-foreground">{ddc}</span>
                </span>
                <span className="font-medium group-hover:text-accent-foreground">{label}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
