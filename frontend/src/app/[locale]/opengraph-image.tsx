import { ImageResponse } from "next/og";

/**
 * The default link preview (checklist step 209): shown when someone shares a BookHive page on
 * WhatsApp, Facebook, etc. Book pages use their cover instead (set in their metadata).
 *
 * English text only: the image renderer's built-in font has Latin letters, and Sinhala or Tamil
 * would need font files embedded here.
 */
export const alt = "BookHive public library";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

// PRD §11 colours
const HONEY = "#F59E0B";
const DEEP_HIVE = "#1F2937";

export default function OpenGraphImage() {
  return new ImageResponse(
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        padding: 80,
        background: DEEP_HIVE,
        color: "white",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 28 }}>
        {/* The hexagon of the logo */}
        <svg width="120" height="120" viewBox="0 0 32 32">
          <path d="M16 1 29 8.5v15L16 31 3 23.5v-15Z" fill={HONEY} />
        </svg>
        <div style={{ fontSize: 96, fontWeight: 700 }}>BookHive</div>
      </div>
      <div style={{ marginTop: 40, fontSize: 48, color: HONEY }}>Every book, one search away</div>
      <div style={{ marginTop: 16, fontSize: 32, opacity: 0.8 }}>
        Search the catalog · See what&apos;s on the shelf · Reserve before you visit
      </div>
    </div>,
    size,
  );
}
