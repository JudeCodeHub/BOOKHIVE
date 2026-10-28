"use client";

import { useMemo } from "react";
import { BarcodeFormat, EncodeHintType, QRCodeWriter } from "@zxing/library";
import { cn } from "@/lib/utils";

/**
 * A QR code drawn as SVG in the browser (zxing, already used by the desk scanner), so it's sharp
 * at any size and works without a network once the page is open. Always black on white, even
 * in dark mode: scanners need the contrast.
 */
export function QrCode({
  value,
  label,
  className,
}: {
  value: string;
  /** What the code is, for screen readers */
  label: string;
  className?: string;
}) {
  const { size, path } = useMemo(() => {
    const hints = new Map<EncodeHintType, unknown>([
      [EncodeHintType.ERROR_CORRECTION, "M"],
      [EncodeHintType.MARGIN, 2],
    ]);
    // Width/height 0: one unit per module; the SVG scales it up
    const matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 0, 0, hints);
    let d = "";
    for (let y = 0; y < matrix.getHeight(); y++) {
      for (let x = 0; x < matrix.getWidth(); x++) {
        if (matrix.get(x, y)) d += `M${x} ${y}h1v1h-1z`;
      }
    }
    return { size: matrix.getWidth(), path: d };
  }, [value]);

  return (
    <svg
      role="img"
      aria-label={label}
      viewBox={`0 0 ${size} ${size}`}
      shapeRendering="crispEdges"
      className={cn("aspect-square bg-white", className)}
    >
      <path d={path} fill="#000" />
    </svg>
  );
}
