"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, CheckCheck } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { TimeAgo } from "@/components/common/time-ago";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useSession } from "@/features/auth/use-session";
import { Link, useRouter } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type NotificationSummary = Schema<"NotificationSummary">;

/** How often the unread count is checked (also on returning to the tab). */
const POLL_MS = 60_000;
/** Messages shown in the dropdown; the rest are on the notifications page. */
const LATEST = 6;

/**
 * The bell (NOTIF-3, checklist step 321): unread count on a badge, checked every minute while the
 * tab is open; opening it shows the latest messages in the site's language. Clicking one marks it
 * read and goes where it points. Only for logged-in users.
 */
export function NotificationBell({ className }: { className?: string }) {
  const t = useTranslations("Notifications");
  const locale = useLocale();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { user } = useSession();

  const unread = useQuery({
    queryKey: ["me", "notifications", "unread"],
    queryFn: () => api.get<{ unread: number }>("/me/notifications/unread-count"),
    enabled: !!user,
    refetchInterval: POLL_MS,
    // A hidden tab doesn't need a live count; it refreshes when you come back
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
  const latest = useQuery({
    queryKey: ["me", "notifications", "latest", locale],
    queryFn: () =>
      api.get<PageResponse<NotificationSummary>>("/me/notifications", {
        query: { lang: locale, page: 0, size: LATEST },
      }),
    // Fetched when the bell is opened (see onOpenChange), not on every page
    enabled: false,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["me", "notifications"] });
  const markRead = useMutation({
    mutationFn: (id: string) => api.post(`/me/notifications/${id}/read`),
    onSettled: refresh,
  });
  const markAllRead = useMutation({
    mutationFn: () => api.post("/me/notifications/read-all"),
    onSettled: refresh,
  });

  if (!user) return null;
  const count = unread.data?.unread ?? 0;
  const items = latest.data?.content ?? [];

  return (
    <DropdownMenu
      onOpenChange={(open) => {
        if (open) void latest.refetch();
      }}
    >
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className={cn("relative", className)}
          aria-label={count > 0 ? t("bellUnread", { count }) : t("bell")}
        >
          <Bell aria-hidden />
          {count > 0 && (
            <span
              aria-hidden
              className="absolute -top-0.5 -right-0.5 flex min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] leading-4 font-semibold text-white"
            >
              {count > 99 ? "99+" : count}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-80">
        <div className="flex items-center justify-between gap-2 px-2 py-1.5">
          <DropdownMenuLabel className="p-0">{t("title")}</DropdownMenuLabel>
          {count > 0 && (
            <Button
              variant="ghost"
              size="sm"
              className="h-7 text-xs"
              onClick={(event) => {
                // Keep the menu open to see them turn read
                event.preventDefault();
                markAllRead.mutate();
              }}
            >
              <CheckCheck aria-hidden />
              {t("markAllRead")}
            </Button>
          )}
        </div>
        <DropdownMenuSeparator />
        {latest.isFetching && items.length === 0 ? (
          <p className="px-2 py-4 text-center text-sm text-muted-foreground">{t("loading")}</p>
        ) : items.length === 0 ? (
          <p className="px-2 py-4 text-center text-sm text-muted-foreground">{t("empty")}</p>
        ) : (
          items.map((n) => (
            <DropdownMenuItem
              key={n.id}
              className="flex flex-col items-start gap-0.5 py-2"
              onSelect={() => {
                if (!n.read) markRead.mutate(n.id);
                if (n.link) router.push(n.link);
              }}
            >
              <span className="flex w-full items-center gap-2">
                {!n.read && (
                  <span
                    aria-label={t("unread")}
                    className="size-2 shrink-0 rounded-full bg-primary"
                  />
                )}
                <span className={cn("text-sm", !n.read && "font-semibold")}>{n.title}</span>
              </span>
              <span className="line-clamp-2 text-xs text-muted-foreground">{n.body}</span>
              <span className="text-[11px] text-muted-foreground">
                <TimeAgo date={n.createdAt} />
              </span>
            </DropdownMenuItem>
          ))
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link href="/account/notifications" className="justify-center text-sm">
            {t("seeAll")}
          </Link>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
