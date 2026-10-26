"use client";

import { BookX, CircleCheck, Trash2 } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { ProblemAlert } from "@/features/desk/member-card";
import type { Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type ItemSummary = Schema<"ItemSummary">;

/**
 * The books scanned for this member (checklist step 238), newest at the top: title, due date if
 * lent today, and each problem right under the book it's about. Nothing is lent until staff
 * confirm (step 239).
 */
export function CheckoutList({
  items,
  memberBlocked,
  onRemove,
}: {
  items: ItemSummary[];
  /** The member can't borrow at all (their card shows why) */
  memberBlocked: boolean;
  onRemove: (barcode: string) => void;
}) {
  const t = useTranslations("Desk");
  const format = useFormatter();
  const ready = items.filter((item) => item.problems.length === 0).length;

  if (items.length === 0) {
    return (
      <p className="rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">
        {t("listEmpty")}
      </p>
    );
  }

  return (
    <section aria-labelledby="desk-list-title" className="grid gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="desk-list-title" className="text-lg font-semibold">
          {t("listTitle", { count: items.length })}
        </h2>
        <p className="text-sm text-muted-foreground">
          {t("listReady", { ready, total: items.length })}
        </p>
      </div>

      <ol className="grid gap-2">
        {[...items].reverse().map((item) => {
          // An unknown barcode comes back without a copy
          const known = item.copyId != null;
          const ok = item.problems.length === 0;
          return (
            <li
              key={item.barcode}
              className={cn(
                "grid gap-2 rounded-xl border bg-card p-3",
                !ok && "border-destructive/40",
              )}
            >
              <div className="flex items-start gap-3">
                {ok ? (
                  <CircleCheck
                    aria-hidden
                    className="mt-0.5 size-5 shrink-0 text-available-foreground"
                  />
                ) : (
                  <BookX aria-hidden className="mt-0.5 size-5 shrink-0 text-destructive" />
                )}
                <div className="grid min-w-0 flex-1 gap-0.5">
                  <p className="font-medium">{known ? item.title : t("unknownBarcode")}</p>
                  <p className="flex flex-wrap gap-x-3 text-sm text-muted-foreground">
                    <span className="font-mono">{item.barcode}</span>
                    {known && item.dueDate && (
                      <span>
                        {t("dueOn", {
                          date: format.dateTime(new Date(item.dueDate), {
                            weekday: "short",
                            day: "numeric",
                            month: "short",
                          }),
                        })}
                      </span>
                    )}
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t("remove", { title: known ? item.title : item.barcode })}
                  onClick={() => onRemove(item.barcode)}
                >
                  <Trash2 aria-hidden />
                </Button>
              </div>
              {known && item.problems.length > 0 && (
                <ul className="grid gap-1.5 pl-8">
                  {item.problems.map((problem) => (
                    <ProblemAlert key={problem.reason} problem={problem} />
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ol>

      <p
        className={cn(
          "rounded-lg px-3 py-2 text-sm",
          ready === items.length && !memberBlocked
            ? "bg-available/15 text-available-foreground"
            : "bg-muted text-muted-foreground",
        )}
      >
        {memberBlocked
          ? t("listMemberBlocked")
          : ready === items.length
            ? t("listAllReady")
            : t("listFixProblems")}
      </p>
    </section>
  );
}
