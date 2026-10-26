"use client";

import "./globals.css";

/**
 * Last resort: only shown if the root layout itself crashes. It replaces the whole page, so it
 * has its own <html>/<body> and can't use translations, so it shows all three languages.
 */
export default function GlobalError({ retry }: { error: unknown; retry: () => void }) {
  return (
    <html lang="en">
      <body className="flex min-h-screen flex-col items-center justify-center gap-2 p-8 text-center">
        <title>BookHive</title>
        <h1 className="text-2xl font-semibold">Something went wrong</h1>
        <p lang="si">යම් දෝෂයක් සිදු විය</p>
        <p lang="ta">ஏதோ தவறு நடந்துவிட்டது</p>
        <button
          type="button"
          onClick={() => retry()}
          className="mt-4 rounded-md bg-primary px-4 py-2 font-medium text-primary-foreground"
        >
          Try again · <span lang="si">නැවත උත්සාහ කරන්න</span> ·{" "}
          <span lang="ta">மீண்டும் முயற்சிக்கவும்</span>
        </button>
      </body>
    </html>
  );
}
