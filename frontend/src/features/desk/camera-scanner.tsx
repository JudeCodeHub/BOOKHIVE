"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { Camera, CameraOff } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

/** The same label stays in front of the camera for a while: report it once per this time. */
const SAME_CODE_COOLDOWN_MS = 2500;

/** Library cards and copy labels are Code 128; the digital card also has a QR code. */
const FORMATS = ["code_128", "qr_code"];

// The browser's own barcode reader (Chrome on Android, macOS, ChromeOS); not in TypeScript's DOM
// types yet
type NativeDetector = { detect: (source: HTMLVideoElement) => Promise<{ rawValue: string }[]> };
type NativeDetectorClass = {
  new (options: { formats: string[] }): NativeDetector;
  getSupportedFormats: () => Promise<string[]>;
};

/** Camera support never changes while the page is open. */
const noSubscription = () => () => {};

/** Stops the camera and the reading loop. */
type Stop = () => void;

/**
 * Reads barcodes from the camera into `video`, calling `onCode` for each one found. Uses the
 * browser's BarcodeDetector when it reads Code 128, otherwise zxing (loaded only now, so desks
 * with a USB scanner never download it).
 */
async function startReading(
  video: HTMLVideoElement,
  onCode: (code: string) => void,
): Promise<Stop> {
  const Native = (window as unknown as { BarcodeDetector?: NativeDetectorClass }).BarcodeDetector;
  const nativeFormats = Native ? await Native.getSupportedFormats().catch(() => []) : [];

  if (Native && nativeFormats.includes("code_128")) {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: "environment" },
    });
    video.srcObject = stream;
    await video.play();
    const detector = new Native({ formats: FORMATS.filter((f) => nativeFormats.includes(f)) });
    let running = true;
    const loop = async () => {
      if (!running) return;
      try {
        const found = await detector.detect(video);
        found.forEach((barcode) => onCode(barcode.rawValue));
      } catch {
        // A frame that can't be read (e.g. still starting): try the next one
      }
      if (running) setTimeout(loop, 150);
    };
    void loop();
    return () => {
      running = false;
      stream.getTracks().forEach((track) => track.stop());
      video.srcObject = null;
    };
  }

  const [{ BrowserMultiFormatReader }, { BarcodeFormat, DecodeHintType }] = await Promise.all([
    import("@zxing/browser"),
    import("@zxing/library"),
  ]);
  const hints = new Map([
    [DecodeHintType.POSSIBLE_FORMATS, [BarcodeFormat.CODE_128, BarcodeFormat.QR_CODE]],
  ]);
  const reader = new BrowserMultiFormatReader(hints);
  const controls = await reader.decodeFromConstraints(
    { video: { facingMode: "environment" } },
    video,
    (result) => {
      if (result) onCode(result.getText());
    },
  );
  return () => controls.stop();
}

/**
 * Camera barcode scanning (CIRC-9, checklist step 242), for a phone or tablet at the desk, or a
 * desk without a USB scanner. Stays open for scanning several books in a row; each code read is
 * handed to the desk like a scanner would type it.
 */
export function CameraScanButton({ onScan }: { onScan: (code: string) => void }) {
  const t = useTranslations("Desk");
  const [open, setOpen] = useState(false);
  // Only offer it where the browser can use a camera at all (false on the server)
  const supported = useSyncExternalStore(
    noSubscription,
    () => !!navigator.mediaDevices?.getUserMedia,
    () => false,
  );

  if (!supported) return null;

  return (
    <>
      <Button
        type="button"
        variant="outline"
        className="h-16 shrink-0 px-4"
        onClick={() => setOpen(true)}
        aria-label={t("cameraOpen")}
      >
        <Camera aria-hidden className="size-6" />
        <span className="hidden sm:inline">{t("camera")}</span>
      </Button>
      <Dialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) document.getElementById("desk-scan")?.focus();
        }}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{t("cameraTitle")}</DialogTitle>
            <DialogDescription>{t("cameraHint")}</DialogDescription>
          </DialogHeader>
          {/* Mounted only while open: closing the dialog turns the camera off */}
          {open && <CameraView onScan={onScan} />}
        </DialogContent>
      </Dialog>
    </>
  );
}

function CameraView({ onScan }: { onScan: (code: string) => void }) {
  const t = useTranslations("Desk");
  const video = useRef<HTMLVideoElement>(null);
  const recent = useRef(new Map<string, number>());
  const [lastCode, setLastCode] = useState<string | null>(null);
  const [failure, setFailure] = useState<"denied" | "noCamera" | "other" | null>(null);
  // The latest onScan without restarting the camera when the desk re-renders
  const onScanRef = useRef(onScan);
  useEffect(() => {
    onScanRef.current = onScan;
  }, [onScan]);

  useEffect(() => {
    let stop: Stop | null = null;
    let cancelled = false;
    const element = video.current;
    if (!element) return;

    startReading(element, (raw) => {
      const code = raw.trim().toUpperCase();
      const now = Date.now();
      if (!code || now - (recent.current.get(code) ?? 0) < SAME_CODE_COOLDOWN_MS) return;
      recent.current.set(code, now);
      setLastCode(code);
      onScanRef.current(code);
    })
      .then((stopReading) => {
        if (cancelled) stopReading();
        else stop = stopReading;
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        const name = error instanceof DOMException ? error.name : "";
        setFailure(
          name === "NotAllowedError"
            ? "denied"
            : name === "NotFoundError" || name === "OverconstrainedError"
              ? "noCamera"
              : "other",
        );
      });

    return () => {
      cancelled = true;
      stop?.();
    };
  }, []);

  if (failure) {
    return (
      <p
        role="alert"
        className="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive"
      >
        <CameraOff aria-hidden className="mt-0.5 size-4 shrink-0" />
        {t(`cameraErrors.${failure}`)}
      </p>
    );
  }

  return (
    <div className="grid gap-3">
      <div className="relative overflow-hidden rounded-lg bg-black">
        <video ref={video} muted playsInline className="aspect-video w-full object-cover" />
        {/* Aim here */}
        <div
          aria-hidden
          className="pointer-events-none absolute inset-x-[12%] top-1/2 h-1/3 -translate-y-1/2 rounded-md border-2 border-primary/80"
        />
      </div>
      <p role="status" aria-live="polite" className="min-h-5 text-sm">
        {lastCode ? t("cameraRead", { code: lastCode }) : t("cameraWaiting")}
      </p>
    </div>
  );
}
