import type { Metadata } from "next";
import { Fraunces, Noto_Sans, Noto_Sans_Sinhala, Noto_Sans_Tamil } from "next/font/google";
import { notFound } from "next/navigation";
import * as rootParams from "next/root-params";
import { hasLocale, NextIntlClientProvider } from "next-intl";
import { getTranslations } from "next-intl/server";
import { QueryProvider } from "@/components/providers/query-provider";
import { SessionProvider } from "@/components/providers/session-provider";
import { ThemeProvider } from "@/components/theme/theme-provider";
import { Toaster } from "@/components/ui/sonner";
import { routing } from "@/i18n/routing";
import { cn } from "@/lib/utils";
import "../globals.css";

// Fonts are downloaded at build time and served from our own domain (no requests to Google).
// globals.css chains them: the browser picks, per character, the first font that has the glyph,
// so English, Sinhala and Tamil text all render correctly in one font stack.
const notoSans = Noto_Sans({ subsets: ["latin"], variable: "--font-noto-sans" });

// Sinhala/Tamil are only needed on /si and /ta pages, so don't preload them on every page.
const notoSinhala = Noto_Sans_Sinhala({
  subsets: ["sinhala"],
  variable: "--font-noto-sinhala",
  preload: false,
});
const notoTamil = Noto_Sans_Tamil({
  subsets: ["tamil"],
  variable: "--font-noto-tamil",
  preload: false,
});

// Serif for headings: the "bookish" feel from PRD §11
const fraunces = Fraunces({ subsets: ["latin"], variable: "--font-fraunces" });

// Pre-render every page in all three languages at build time (required with Cache Components)
export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Metadata");
  return {
    title: t("title"),
    description: t("description"),
    // Makes relative links in metadata (canonical, other languages, preview images) absolute
    metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000"),
    // Link previews when pages are shared (the image is opengraph-image.tsx, or a book's cover)
    openGraph: {
      type: "website",
      siteName: "BookHive",
      title: t("title"),
      description: t("description"),
    },
    twitter: { card: "summary_large_image" },
  };
}

/** Root layout: everything under /en, /si, /ta. */
export default async function LocaleLayout({ children }: LayoutProps<"/[locale]">) {
  const locale = await rootParams.locale();
  if (!hasLocale(routing.locales, locale)) {
    notFound();
  }

  return (
    <html
      lang={locale}
      className={cn(
        "h-full antialiased",
        notoSans.variable,
        notoSinhala.variable,
        notoTamil.variable,
        fraunces.variable,
      )}
      // next-themes adds class="dark" before React loads; this stops React warning about it
      suppressHydrationWarning
    >
      <body className="flex min-h-full flex-col">
        {/* Passes the language and messages on to client components (useTranslations) */}
        <NextIntlClientProvider>
          <ThemeProvider>
            <QueryProvider>
              <SessionProvider>{children}</SessionProvider>
              {/* Pop-up notifications: toast.success("Book renewed") from any client component */}
              <Toaster richColors closeButton />
            </QueryProvider>
          </ThemeProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
