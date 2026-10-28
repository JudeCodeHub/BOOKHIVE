"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ImageUp, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { HexLoader } from "@/components/brand/hex-loader";
import { UploadProgress } from "@/components/forms/file-upload";
import { Button } from "@/components/ui/button";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { api } from "@/lib/api";

const MAX_BYTES = 5 * 1024 * 1024;

/**
 * A staff picture for a record (event poster, news picture: steps 344, 347). Shows the current
 * one as staff see it (drafts too, so loaded with the login), and uploads or removes it at
 * `path` (POST multipart "image", DELETE); the API answers with the updated record.
 *
 * @param version changes when the picture changes, so the preview is fetched again
 */
export function PictureUpload<T>({
  path,
  hasImage,
  version,
  hint,
  disabled = false,
  onChange,
}: {
  path: string;
  hasImage: boolean;
  version: string | null | undefined;
  hint: string;
  disabled?: boolean;
  onChange: (updated: T) => void;
}) {
  const t = useTranslations("PictureUpload");
  const errorMessage = useAuthErrorMessage();
  const inputRef = useRef<HTMLInputElement>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  const image = useQuery({
    queryKey: ["staff", "picture", path, version],
    queryFn: () => api.getBlob(path),
    enabled: hasImage,
    staleTime: Infinity,
  });
  const url = useMemo(() => (image.data ? URL.createObjectURL(image.data) : null), [image.data]);
  useEffect(
    () => () => {
      if (url) URL.revokeObjectURL(url);
    },
    [url],
  );

  async function upload(file: File | undefined) {
    if (inputRef.current) inputRef.current.value = "";
    if (!file) return;
    if (!["image/jpeg", "image/png"].includes(file.type)) return void toast.error(t("wrongType"));
    if (file.size > MAX_BYTES) return void toast.error(t("tooLarge"));
    setBusy(true);
    try {
      const body = new FormData();
      body.append("image", file);
      onChange(await api.post<T>(path, body, { onUploadProgress: setProgress }));
      toast.success(t("saved"));
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
      onChange(await api.delete<T>(path));
      toast.success(t("removed"));
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid max-w-xl justify-items-start gap-3">
      <p className="text-sm text-muted-foreground">{hint}</p>
      {hasImage && url ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt={t("current")} className="w-full rounded-lg border" />
      ) : (
        <div className="flex aspect-video w-full items-center justify-center rounded-lg border border-dashed text-sm text-muted-foreground">
          {hasImage ? <HexLoader label={t("current")} /> : t("none")}
        </div>
      )}
      {progress !== null && <UploadProgress value={progress} />}
      {!disabled && (
        <div className="flex flex-wrap gap-2">
          <Button
            size="sm"
            variant="secondary"
            disabled={busy}
            onClick={() => inputRef.current?.click()}
          >
            <ImageUp aria-hidden />
            {hasImage ? t("replace") : t("upload")}
          </Button>
          {hasImage && (
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => void remove()}>
              <Trash2 aria-hidden />
              {t("remove")}
            </Button>
          )}
        </div>
      )}
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png"
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        onChange={(e) => void upload(e.target.files?.[0])}
      />
    </div>
  );
}
