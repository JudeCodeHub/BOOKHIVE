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
      {...props}
    />
  );
}
