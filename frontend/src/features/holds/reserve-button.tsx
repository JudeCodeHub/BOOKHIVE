"use client";

import type { ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BookmarkPlus, CircleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { useSession } from "@/features/auth/use-session";
import { Link, useRouter } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";

type Availability = Schema<"AvailabilityResponse">;
type HoldCheck = Schema<"HoldCheckResponse">;
type HoldSummary = Schema<"HoldSummary">;

/**
 * "Reserve" on the book page (HOLD-1, checklist step 261). Shown only when every lendable copy is
 * out: with one on the shelf, members simply come and borrow it. Says how many are already
 * waiting, and why reserving isn't possible when it isn't (e.g. 3 of 3 holds used).
 */
export function ReserveButton({ bookId }: { bookId: string }) {
  const t = useTranslations("Reserve");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const session = useSession();
  const router = useRouter();
  // Same query as the availability box above, so it's shared, not fetched twice
  const availability = useQuery({
    queryKey: ["availability", bookId],
    queryFn: () => api.get<Availability>(`/books/${bookId}/availability`),
    refetchInterval: 60_000,
  });
  const a = availability.data;
  const lendable = a ? a.total - a.referenceOnly : 0;
  const allOut = a !== undefined && lendable > 0 && a.availableToBorrow === 0;
  const loggedIn = session.status === "authenticated";

  const check = useQuery({
    queryKey: ["me", "holds", "check", bookId],
    queryFn: () => api.get<HoldCheck>(`/me/holds/check/${bookId}`),
    enabled: allOut && loggedIn,
    retry: false,
  });

  const reserve = useMutation({
    mutationFn: () => api.post<HoldSummary>("/me/holds", { bookId }),
    onSuccess: (hold) => {
      toast.success(t("reserved", { position: hold.position ?? 1 }), {
        action: { label: t("seeHolds"), onClick: () => router.push("/account/holds") },
      });
      void queryClient.invalidateQueries({ queryKey: ["me", "holds"] });
      void queryClient.invalidateQueries({ queryKey: ["availability", bookId] });
    },
    onError: (error) => {
      toast.error(errorMessage(error));
      void check.refetch();
    },
  });

  if (!allOut || session.status === "loading") return null;

  const box = (children: ReactNode) => (
    <div className="mt-3 grid gap-2 border-t pt-3">{children}</div>
  );
  const waiting = a.holdsWaiting;

  if (!loggedIn) {
    return box(
      <>
        <p className="text-sm text-muted-foreground">{t("intro", { count: waiting })}</p>
        <Button asChild variant="outline">
          <Link href={`/login?next=${encodeURIComponent(`/books/${bookId}`)}`}>
            {t("logInToReserve")}
          </Link>
        </Button>
      </>,
    );
  }

  if (check.isError) {
    const notMember = check.error instanceof ApiError && check.error.code === "MEMBER_NOT_FOUND";
    return box(
      notMember ? (
        <p className="text-sm">
          {t.rich("notMember", {
            link: (chunks) => (
              <Link href="/account/membership" className="font-medium text-primary underline">
                {chunks}
              </Link>
            ),
          })}
        </p>
      ) : (
        <p className="text-sm text-muted-foreground">{errorMessage(check.error)}</p>
      ),
    );
  }
  if (!check.data) return null;

  const problems = check.data.problems;
  if (problems.some((p) => p.reason === "ALREADY_HELD")) {
    return box(
      <p className="text-sm">
        {t.rich("alreadyHeld", {
          link: (chunks) => (
            <Link href="/account/holds" className="font-medium text-primary underline">
              {chunks}
            </Link>
          ),
        })}
      </p>,
    );
  }

  return box(
    <>
      <p className="text-sm text-muted-foreground">{t("intro", { count: waiting })}</p>
      {check.data.canPlace ? (
        <Button onClick={() => reserve.mutate()} disabled={reserve.isPending}>
          <BookmarkPlus aria-hidden />
          {reserve.isPending ? t("reserving") : t("reserve")}
        </Button>
      ) : (
        <ul className="grid gap-1 text-sm" aria-label={t("cantReserve")}>
          {problems.map((p) => (
            <li key={p.reason} className="flex items-start gap-1.5">
              <CircleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-on-loan-foreground" />
              {t(`problems.${p.reason}`)}
            </li>
          ))}
        </ul>
      )}
    </>,
  );
}
