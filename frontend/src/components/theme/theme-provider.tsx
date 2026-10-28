"use client";

import type { ComponentProps } from "react";
import { ThemeProvider as NextThemesProvider } from "next-themes";

/**
 * Light / dark mode. Adds class="dark" to <html> when dark, which switches the colour tokens in
 * globals.css. Starts from the device setting and remembers the user's choice.
 */
export function ThemeProvider(props: ComponentProps<typeof NextThemesProvider>) {
  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      // No colour fade on every element when switching; it looks glitchy
      disableTransitionOnChange
      // next-themes' inline script sets the theme class before React loads (no flash of the
      // wrong colours). It only matters in the server's HTML. When the browser renders the layout
      // itself (e.g. after switching language), React warns about script tags; there the script
      // is useless anyway (next-themes keeps the class up to date), so it's marked as plain text.
      scriptProps={{
        type: typeof window === "undefined" ? "text/javascript" : "text/plain",
        suppressHydrationWarning: true,
      }}
      {...props}
    />
  );
}
