"use client";

import { Moon, Sun } from "lucide-react";
import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Switches between light and dark. The icon is picked with CSS (dark: classes), not from
 * JavaScript state, so the server-rendered HTML already shows the right icon: no flicker.
 */
export function ThemeToggle({ className }: { className?: string }) {
  const t = useTranslations("Header");
  const { resolvedTheme, setTheme } = useTheme();

  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      aria-label={t("toggleTheme")}
      title={t("toggleTheme")}
      className={cn("text-inherit", className)}
      onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
    >
      <Sun className="dark:hidden" />
      <Moon className="hidden dark:block" />
    </Button>
  );
}
