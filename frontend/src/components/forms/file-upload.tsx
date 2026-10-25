"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { FileText, ImageUp, X } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import type { FieldControlProps } from "@/components/forms/form-field";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Pick one file by tapping or dragging it in, see a preview, and watch the upload progress
 * (checklist step 139). Use inside FormField, which provides the label, hint and error:
 *
 *   <FormField label="Photo of your NIC" error={fileError}>
 *     {(field) => <FileUpload {...field} value={file} onChange={setFile} accept="image/*" />}
 *   </FormField>
 *
 * The preview is made in the browser (nothing is uploaded until the form is sent).
 */
export function FileUpload({
  value,
  onChange,
  accept,
  progress,
  disabled,
  ...field
}: FieldControlProps & {
  value: File | null;
  onChange: (file: File | null) => void;
  /** e.g. "image/jpeg,image/png,application/pdf" */
  accept?: string;
  /** 0..1 while uploading, otherwise null/undefined */
  progress?: number | null;
  disabled?: boolean;
}) {
  const t = useTranslations("FileUpload");
  const format = useFormatter();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const preview = useObjectUrl(value?.type.startsWith("image/") ? value : null);

  function pick(files: FileList | null) {
    onChange(files?.[0] ?? null);
  }

  return (
    <div className="grid gap-3">
      {value ? (
        <div className="flex items-center gap-3 rounded-lg border p-3">
          {preview ? (
            // A local preview (blob: URL) of the user's own photo; next/image can't optimise it
            // eslint-disable-next-line @next/next/no-img-element
            <img src={preview} alt={t("preview")} className="size-20 rounded-md object-cover" />
          ) : (
            <FileText aria-hidden className="size-10 shrink-0 text-primary" />
          )}
          <div className="min-w-0 flex-1 text-sm">
            <p className="truncate font-medium">{value.name}</p>
            <p className="text-muted-foreground">
              {format.number(value.size / (1024 * 1024), { maximumFractionDigits: 1 })} MB
            </p>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            disabled={disabled}
            aria-label={t("remove", { name: value.name })}
            onClick={() => {
              onChange(null);
              if (inputRef.current) inputRef.current.value = "";
            }}
          >
            <X />
          </Button>
        </div>
      ) : (
        <label
          htmlFor={field.id}
          onDragOver={(event) => {
            event.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDragging(false);
            pick(event.dataTransfer.files);
          }}
          className={cn(
            "flex cursor-pointer flex-col items-center gap-2 rounded-lg border-2 border-dashed p-6 text-center text-sm transition-colors",
            "hover:border-primary hover:bg-primary/5 has-focus-visible:ring-3 has-focus-visible:ring-ring/50",
            dragging && "border-primary bg-primary/10",
            field["aria-invalid"] && "border-destructive",
          )}
        >
          <ImageUp aria-hidden className="size-8 text-primary" />
          <span className="font-medium">{t("choose")}</span>
          <span className="text-muted-foreground">{t("drop")}</span>
        </label>
      )}

      {/* The real input: visually hidden but focusable, so keyboards and screen readers work */}
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        disabled={disabled}
        className="sr-only"
        {...field}
        onChange={(event) => pick(event.target.files)}
      />

      {progress != null && <UploadProgress value={progress} />}
    </div>
  );
}

/** Upload progress bar with a percentage, read out by screen readers. */
export function UploadProgress({ value }: { value: number }) {
  const t = useTranslations("FileUpload");
  const percent = Math.round(value * 100);

  return (
    <div className="grid gap-1">
      <div
        role="progressbar"
        aria-label={t("uploading")}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
        className="h-2 overflow-hidden rounded-full bg-muted"
      >
        <div className="h-full bg-primary transition-[width]" style={{ width: `${percent}%` }} />
      </div>
      <p className="text-xs text-muted-foreground">{t("progress", { percent })}</p>
    </div>
  );
}

/** A temporary blob: URL for a file, released when the file changes or the component goes. */
function useObjectUrl(file: File | null) {
  const url = useMemo(() => (file ? URL.createObjectURL(file) : null), [file]);
  useEffect(
    () => () => {
      if (url) URL.revokeObjectURL(url);
    },
    [url],
  );
  return url;
}
