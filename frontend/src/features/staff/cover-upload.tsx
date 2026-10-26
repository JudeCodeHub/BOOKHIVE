"use client";

import { useRef, useState } from "react";
import { BookOpen, ImageUp, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import Cropper, { type Area } from "react-easy-crop";
import { toast } from "sonner";
import { UploadProgress } from "@/components/forms/file-upload";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { BookCover } from "@/features/catalog/book-cover";
import { coverUrl } from "@/features/catalog/cover";
import { api, type Schema } from "@/lib/api";

type Book = Schema<"BookResponse">;

/** Book covers are portrait, about 2:3 (width:height). */
const ASPECT = 2 / 3;
/** Plenty for the largest stored size (800 px wide); keeps uploads small on mobile data. */
const MAX_HEIGHT = 1500;
const MAX_FILE_BYTES = 5 * 1024 * 1024;

/**
 * Cover upload with crop (checklist step 176). Staff pick a photo (often taken with a phone, with
 * table around the book), drag and zoom it into a book-shaped frame, and only the cropped part
 * is uploaded. The backend then makes the small/medium/large sizes (step 161).
 */
export function CoverUpload({ book, onChange }: { book: Book; onChange: (book: Book) => void }) {
  const t = useTranslations("CoverUpload");
  const errorMessage = useAuthErrorMessage();
  const inputRef = useRef<HTMLInputElement>(null);
  const [source, setSource] = useState<string | null>(null);
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [area, setArea] = useState<Area | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  const current = coverUrl(book.id, book.coverVersion, "medium");

  function pick(file: File | undefined) {
    if (!file) return;
    if (!["image/jpeg", "image/png"].includes(file.type)) {
      toast.error(t("wrongType"));
      return;
    }
    if (file.size > MAX_FILE_BYTES * 4) {
      // Cropping in the browser shrinks big phone photos; only refuse really huge files
      toast.error(t("tooLarge"));
      return;
    }
    setSource(URL.createObjectURL(file));
    setCrop({ x: 0, y: 0 });
    setZoom(1);
  }

  function close() {
    if (source) URL.revokeObjectURL(source);
    setSource(null);
    if (inputRef.current) inputRef.current.value = "";
  }

  async function save() {
    if (!source || !area) return;
    setBusy(true);
    try {
      const blob = await cropToJpeg(source, area);
      const body = new FormData();
      body.append("cover", blob, "cover.jpg");
      const updated = await api.post<Book>(`/staff/books/${book.id}/cover`, body, {
        onUploadProgress: setProgress,
      });
      onChange(updated);
      toast.success(t("saved"));
      close();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
      setProgress(null);
    }
  }

  async function remove() {
    setBusy(true);
    try {
      onChange(await api.delete<Book>(`/staff/books/${book.id}/cover`));
      toast.success(t("removed"));
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid justify-items-start gap-3">
      {current ? (
        <BookCover
          bookId={book.id}
          version={book.coverVersion}
          alt={t("current")}
          sizes="160px"
          className="w-40 border shadow-sm"
        />
      ) : (
        <div className="flex h-60 w-40 flex-col items-center justify-center gap-2 rounded-md border border-dashed text-sm text-muted-foreground">
          <BookOpen aria-hidden className="size-8" />
          {t("none")}
        </div>
      )}
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={busy}
          onClick={() => inputRef.current?.click()}
        >
          <ImageUp aria-hidden />
          {current ? t("replace") : t("upload")}
        </Button>
        {current && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={busy}
            onClick={() => void remove()}
          >
            <Trash2 aria-hidden />
            {t("remove")}
          </Button>
        )}
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png"
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        onChange={(event) => pick(event.target.files?.[0])}
      />

      <Dialog open={source !== null} onOpenChange={(open) => !open && !busy && close()}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{t("cropTitle")}</DialogTitle>
            <DialogDescription>{t("cropHelp")}</DialogDescription>
          </DialogHeader>
          <div className="relative h-80 overflow-hidden rounded-md bg-muted">
            {source && (
              <Cropper
                image={source}
                crop={crop}
                zoom={zoom}
                aspect={ASPECT}
                onCropChange={setCrop}
                onZoomChange={setZoom}
                onCropComplete={(_, pixels) => setArea(pixels)}
              />
            )}
          </div>
          <label className="grid gap-1 text-sm">
            {t("zoom")}
            <input
              type="range"
              min={1}
              max={3}
              step={0.05}
              value={zoom}
              onChange={(event) => setZoom(Number(event.target.value))}
              className="accent-primary"
            />
          </label>
          {progress !== null && <UploadProgress value={progress} />}
          <DialogFooter>
            <Button type="button" variant="secondary" disabled={busy} onClick={close}>
              {t("cancel")}
            </Button>
            <Button type="button" disabled={busy || !area} onClick={() => void save()}>
              {busy ? t("saving") : t("save")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** Draws the chosen part of the picture onto a canvas and returns it as a JPEG. */
async function cropToJpeg(source: string, area: Area): Promise<Blob> {
  const image = new Image();
  image.src = source;
  await image.decode();

  const scale = Math.min(1, MAX_HEIGHT / area.height);
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(area.width * scale);
  canvas.height = Math.round(area.height * scale);
  const context = canvas.getContext("2d");
  if (!context) throw new Error("Canvas not available");
  // White behind transparent PNGs, like the backend does
  context.fillStyle = "#fff";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.drawImage(
    image,
    area.x,
    area.y,
    area.width,
    area.height,
    0,
    0,
    canvas.width,
    canvas.height,
  );
  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("Could not make the picture"))),
      "image/jpeg",
      0.9,
    ),
  );
}
