import type { Metadata } from "next";
import { Fraunces, Noto_Sans, Noto_Sans_Sinhala, Noto_Sans_Tamil } from "next/font/google";
import { ThemeProvider } from "@/components/theme/theme-provider";
import { Toaster } from "@/components/ui/sonner";
import { cn } from "@/lib/utils";
import "./globals.css";

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

export const metadata: Metadata = {
  title: "BookHive",
  description:
    "Search the catalog, check availability, and manage your loans, holds and fines online.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
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
        <ThemeProvider>
          {children}
          {/* Pop-up notifications: toast.success("Book renewed") from any client component */}
          <Toaster richColors closeButton />
        </ThemeProvider>
      </body>
    </html>
  );
}
