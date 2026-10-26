"use client";

import Image, { type ImageLoader } from "next/image";
import { BookOpen } from "lucide-react";
import { coverUrl } from "@/features/catalog/cover";
import { cn } from "@/lib/utils";

/** The API stores each cover at these widths (CoverService, step 161). */
const SIZES = [
  { name: "small", width: 128 },
  { name: "medium", width: 320 },
  { name: "large", width: 800 },
] as const;

/**
 * next/image asks for the width it needs (from `sizes` and the screen's pixel density); answer
 * with the smallest stored cover that's at least that wide. No image server needed: the API has
 * already made the sizes, and caches them for a year.
 */
const coverLoader: ImageLoader = ({ src, width }) => {
  const size = SIZES.find((s) => s.width >= width) ?? SIZES[SIZES.length - 1];
  return src.replace("/cover/medium", `/cover/${size.name}`);
};

/**
 * A book cover (checklist step 210): responsive (the right file for the screen), lazy-loaded
 * below the fold, and with its 2:3 space reserved, so the page doesn't jump while it loads.
 * Books without a cover get a book icon of the same shape.
 *
 * @param sizes how wide it's shown, e.g. "(min-width: 640px) 200px, 80px"
 * @param priority true for the one big cover at the top of a page (loads first, not lazily)
 */
export function BookCover({
  bookId,
  version,
  alt,
  sizes,
  priority = false,
  className,
}: {
  bookId: string;
  version: string | null | undefined;
  alt: string;
  sizes: string;
  priority?: boolean;
  className?: string;
}) {
  const src = coverUrl(bookId, version, "medium");
  if (!src) {
    return (
      <div
        role={alt ? "img" : undefined}
        aria-label={alt || undefined}
        aria-hidden={alt ? undefined : true}
        className={cn("flex aspect-[2/3] items-center justify-center rounded bg-muted", className)}
      >
        <BookOpen aria-hidden className="size-1/3 max-h-12 max-w-12 text-muted-foreground" />
      </div>
    );
  }
  return (
    <Image
      src={src}
      loader={coverLoader}
      alt={alt}
      // The intrinsic 2:3 shape: reserves the space before the file arrives
      width={800}
      height={1200}
      sizes={sizes}
      priority={priority}
      className={cn("aspect-[2/3] h-auto rounded object-cover", className)}
    />
  );
}
