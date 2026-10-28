"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellOff, Check, CheckCheck, ChevronRight } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import { HexLoader } from "@/components/brand/hex-loader";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Pager } from "@/features/staff/pager";
import { Link } from "@/i18n/navigation";
import { api, type PageResponse, type Schema } from "@/lib/api";
import { cn } from "@/lib/utils";

type NotificationSummary = Schema<"NotificationSummary">;

/**
 * All my notifications (checklist step 322), newest first, in the site's language: open the page
 * a message points to, mark one read, or mark all read. Same data as the bell, so marking here
 * updates its badge too.
 */
export function NotificationsPage() {
  const t = useTranslations("Notifications");
  const tCommon = useTranslations("Common");
  const format = useFormatter();
  const locale = useLocale();
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  const list = useQuery({
    queryKey: ["me", "notifications", "page", locale, page],
    queryFn: () =>
      api.get<PageResponse<NotificationSummary>>("/me/notifications", {
        query: { lang: locale, page, size: 20 },
      }),
    placeholderData: keepPreviousData,
  });
  const unread = useQuery({
    queryKey: ["me", "notifications", "unread"],
    queryFn: () => api.get<{ unread: number }>("/me/notifications/unread-count"),
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

  if (list.isPending) {
    return (
      <div className="flex justify-center py-8">
        <HexLoader label={tCommon("loading")} />
      </div>
    );
  }
  if (list.isError) return <FormAlert>{errorMessage(list.error)}</FormAlert>;
  const data = list.data;

  if (data.totalElements === 0) {
    return (
      <p className="flex flex-col items-center gap-2 rounded-lg border p-8 text-center text-muted-foreground">
        <BellOff aria-hidden className="size-6" />
        {t("emptyPage")}
      </p>
    );
  }

  const unreadCount = unread.data?.unread ?? 0;

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          {unreadCount > 0 ? t("unreadSummary", { count: unreadCount }) : t("allRead")}
        </p>
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="ghost" size="sm">
            <Link href="/account/notifications/settings">{t("settingsLink")}</Link>
          </Button>
          {unreadCount > 0 && (
            <Button
              variant="outline"
              size="sm"
              disabled={markAllRead.isPending}
              onClick={() => markAllRead.mutate()}
            >
              <CheckCheck aria-hidden />
              {t("markAllRead")}
            </Button>
          )}
        </div>
      </div>

      <ul className="grid gap-2">
        {data.content.map((n) => (
          <li
            key={n.id}
            className={cn(
              "flex flex-wrap items-start gap-3 rounded-xl border bg-card p-3 sm:p-4",
              !n.read && "border-primary/50 bg-primary/5",
            )}
          >
            {!n.read && (
              <span
                aria-label={t("unread")}
                className="mt-2 size-2 shrink-0 rounded-full bg-primary"
              />
            )}
            <div className="grid min-w-0 flex-1 gap-0.5">
              <p className={cn(!n.read && "font-semibold")}>{n.title}</p>
              <p className="text-sm text-muted-foreground">{n.body}</p>
              <p className="text-xs text-muted-foreground">
                {format.dateTime(new Date(n.createdAt), {
                  dateStyle: "medium",
                  timeStyle: "short",
                })}
              </p>
            </div>
            <div className="flex gap-1">
              {!n.read && (
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={markRead.isPending && markRead.variables === n.id}
                  onClick={() => markRead.mutate(n.id)}
                >
                  <Check aria-hidden />
                  {t("markRead")}
                  <span className="sr-only">: {n.title}</span>
                </Button>
              )}
              {n.link && (
                <Button asChild variant="outline" size="sm">
                  <Link
                    href={n.link}
                    onClick={() => {
                      if (!n.read) markRead.mutate(n.id);
                    }}
                  >
                    {t("open")}
                    <ChevronRight aria-hidden />
                    <span className="sr-only">: {n.title}</span>
                  </Link>
                </Button>
              )}
            </div>
          </li>
        ))}
      </ul>
      <Pager page={page} totalPages={data.totalPages} onChange={setPage} />
    </div>
  );
}
