"use client";

import {
  ChevronDown,
  CircleUserRound,
  LayoutDashboard,
  LogOut,
  ScanBarcode,
  SlidersHorizontal,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { logout } from "@/features/auth/session";
import { openableAreas, type Area } from "@/features/auth/session-hint";
import { useSession } from "@/features/auth/use-session";
import { Link, useRouter } from "@/i18n/navigation";
import { cn } from "@/lib/utils";

const AREA_ICONS: Record<Area, React.ReactNode> = {
  account: <LayoutDashboard />,
  staff: <ScanBarcode />,
  admin: <SlidersHorizontal />,
};

/**
 * Right side of the header (checklist steps 115-116): "Log in" for visitors; for a logged-in user
 * a menu with only the areas their roles can open (a member never sees the staff console) and
 * "Log out".
 */
export function AccountMenu({ className }: { className?: string }) {
  const t = useTranslations("AccountMenu");
  const tHeader = useTranslations("Header");
  const { status, user } = useSession();
  const router = useRouter();

  if (status === "loading") {
    // Same size as the real button, so the header doesn't jump when the session settles
    return <span aria-hidden className="h-8 w-20 animate-pulse rounded-lg bg-muted/60" />;
  }
  if (!user) {
    return (
      <Button asChild variant="secondary" className={className}>
        <Link href="/login">{tHeader("login")}</Link>
      </Button>
    );
  }

  async function handleLogout() {
    await logout();
    // Back to the public home page; other open tabs log out too (BroadcastChannel)
    router.replace("/");
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="secondary" className={cn("gap-1.5", className)}>
          <CircleUserRound aria-hidden />
          <span className="max-w-32 truncate">{user.fullName.split(" ")[0]}</span>
          <ChevronDown aria-hidden className="opacity-60" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuLabel className="font-normal">
          <p className="truncate font-medium text-foreground">{user.fullName}</p>
          <p className="truncate text-xs text-muted-foreground">{user.email}</p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {openableAreas(user.roles).map((area) => (
          <DropdownMenuItem key={area} asChild>
            <Link href={`/${area}`}>
              {AREA_ICONS[area]}
              {t(area)}
            </Link>
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => void handleLogout()}>
          <LogOut />
          {t("logout")}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
