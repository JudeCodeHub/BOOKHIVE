"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Heart } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { useSession } from "@/features/auth/use-session";
import { useRouter } from "@/i18n/navigation";
import { ApiError, api, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type Status = Schema<"WishlistStatusResponse">;

/**
 * The heart on the book page (COMM-3, checklist step 350): save a book to "read later". Not
 * logged in: it leads to the login and back. The heart fills at once and goes back if saving
 * fails.
 */
export function WishlistButton({ bookId }: { bookId: string }) {
  const t = useTranslations("Wishlist");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const router = useRouter();
  const session = useSession();
  const loggedIn = session.status === "authenticated";
  const key = ["me", "wishlist", "status", bookId];

  const status = useQuery({
    queryKey: key,
    queryFn: () => api.get<Status>(`/me/wishlist/${bookId}`),
    enabled: loggedIn,
    retry: false,
  });

  const toggle = useMutation({
    mutationFn: (save: boolean) =>
      save ? api.post("/me/wishlist", { bookId }) : api.delete(`/me/wishlist/${bookId}`),
    onMutate: (save) => {
      queryClient.setQueryData<Status>(key, { saved: save });
    },
    onSuccess: (_, save) => {
      toast.success(save ? t("added") : t("removed"), {
        action: save
          ? { label: t("seeWishlist"), onClick: () => router.push("/account/wishlist") }
          : undefined,
      });
    },
    onError: (error, save) => {
      queryClient.setQueryData<Status>(key, { saved: !save });
      const code = error instanceof ApiError ? error.code : undefined;
      toast.error(code === "WISHLIST_FULL" ? t("full") : errorMessage(error));
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ["me", "wishlist", "list"] });
    },
  });

  if (session.status === "loading") return null;
  // Staff without a membership: no wishlist
  if (loggedIn && status.isError) return null;

  const saved = status.data?.saved ?? false;
  return (
    <Button
      type="button"
      variant="outline"
      size="sm"
      aria-pressed={loggedIn ? saved : undefined}
      disabled={loggedIn && (status.isPending || toggle.isPending)}
      onClick={() => {
        if (!loggedIn) {
          router.push(`/login?next=${encodeURIComponent(`/books/${bookId}`)}`);
          return;
        }
        toggle.mutate(!saved);
      }}
    >
      <Heart aria-hidden className={cn(saved && "fill-red-500 text-red-500")} />
      {saved ? t("saved") : t("save")}
    </Button>
  );
}
