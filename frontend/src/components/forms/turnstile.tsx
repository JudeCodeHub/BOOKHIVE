"use client";

import { useEffect, useImperativeHandle, useRef, type Ref } from "react";
import { useLocale } from "next-intl";
import { useTheme } from "next-themes";

/**
 * Cloudflare Turnstile, the "I'm not a robot" check (checklist step 207). Usually invisible or a
 * single tick; the token it gives is checked by our backend with Cloudflare.
 *
 * Dev uses Cloudflare's test site key, which always passes. Prod: NEXT_PUBLIC_TURNSTILE_SITE_KEY.
 */
const SITE_KEY = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY ?? "1x00000000000000000000AA";
const SCRIPT_URL = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

type TurnstileApi = {
  render: (element: HTMLElement, options: Record<string, unknown>) => string;
  reset: (widgetId: string) => void;
  remove: (widgetId: string) => void;
};

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

/** The script is shared by every widget on the page; load it once. */
let scriptPromise: Promise<TurnstileApi> | null = null;
function loadTurnstile(): Promise<TurnstileApi> {
  scriptPromise ??= new Promise((resolve, reject) => {
    if (window.turnstile) return resolve(window.turnstile);
    const script = document.createElement("script");
    script.src = SCRIPT_URL;
    script.async = true;
    script.onload = () =>
      window.turnstile ? resolve(window.turnstile) : reject(new Error("Turnstile"));
    script.onerror = () => {
      scriptPromise = null;
      reject(new Error("Turnstile script failed to load"));
    };
    document.head.appendChild(script);
  });
  return scriptPromise;
}

export type TurnstileHandle = { reset: () => void };

export function Turnstile({
  onToken,
  ref,
}: {
  /** The token when the check passes; null when it expires or fails */
  onToken: (token: string | null) => void;
  ref?: Ref<TurnstileHandle>;
}) {
  const container = useRef<HTMLDivElement>(null);
  const widget = useRef<string | null>(null);
  const onTokenRef = useRef(onToken);
  const locale = useLocale();
  const { resolvedTheme } = useTheme();

  useEffect(() => {
    onTokenRef.current = onToken;
  });

  useImperativeHandle(ref, () => ({
    // Each token works once: get a fresh one after the form is sent
    reset: () => {
      if (widget.current && window.turnstile) window.turnstile.reset(widget.current);
      onTokenRef.current(null);
    },
  }));

  useEffect(() => {
    let cancelled = false;
    loadTurnstile()
      .then((api) => {
        if (cancelled || !container.current) return;
        widget.current = api.render(container.current, {
          sitekey: SITE_KEY,
          theme: resolvedTheme === "dark" ? "dark" : "light",
          // Turnstile has English and Tamil but not Sinhala; it picks the closest itself
          language: locale === "ta" ? "ta" : locale === "si" ? "auto" : "en",
          callback: (token: string) => onTokenRef.current(token),
          "expired-callback": () => onTokenRef.current(null),
          "error-callback": () => onTokenRef.current(null),
        });
      })
      .catch(() => onTokenRef.current(null));
    return () => {
      cancelled = true;
      if (widget.current && window.turnstile) window.turnstile.remove(widget.current);
      widget.current = null;
    };
  }, [locale, resolvedTheme]);

  return <div ref={container} className="min-h-[65px]" />;
}
