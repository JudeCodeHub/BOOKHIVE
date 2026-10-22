import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "BookHive",
  description:
    "Search the catalog, check availability, and manage your loans, holds and fines online.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="flex min-h-full flex-col">{children}</body>
    </html>
  );
}
