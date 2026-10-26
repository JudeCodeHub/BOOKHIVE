"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { FileUpload, UploadProgress } from "@/components/forms/file-upload";
import { FormField } from "@/components/forms/form-field";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { FormAlert } from "@/features/auth/auth-card";
import { useAuthErrorMessage } from "@/features/auth/errors";
import { Link } from "@/i18n/navigation";
import { api, type Schema } from "@/lib/api";

type ImportReport = Schema<"ImportReport">;

const OUTCOME_VARIANT = {
  CREATED: "available",
  COPIES_ADDED: "reference",
  FAILED: "overdue",
} as const;

/**
 * CSV import in two steps (checklist step 180): "Check file" runs the whole import as a dry run
 * (nothing saved) and shows every row's result; "Import" then does it for real, skipping the
 * rows with problems.
 */
export function CatalogImport() {
  const t = useTranslations("CatalogImport");
  const errorMessage = useAuthErrorMessage();
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [onlyProblems, setOnlyProblems] = useState(false);

  async function run(dryRun: boolean) {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const body = new FormData();
      body.append("file", file);
      const result = await api.post<ImportReport>("/staff/books/import", body, {
        query: { dryRun },
        onUploadProgress: setProgress,
        // A big file takes a while: every row is checked like a real import
        timeoutMs: 300_000,
      });
      setReport(result);
      setOnlyProblems(result.failed > 0);
      if (!dryRun) {
        toast.success(t("imported", { created: result.created, copies: result.copiesAdded }));
        await queryClient.invalidateQueries({ queryKey: ["staff", "catalog"] });
      }
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
      setProgress(null);
    }
  }

  async function downloadTemplate() {
    const csv = await api.getBlob("/staff/books/import/template.csv");
    const url = URL.createObjectURL(csv);
    const link = document.createElement("a");
    link.href = url;
    link.download = "bookhive-import-template.csv";
    link.click();
    URL.revokeObjectURL(url);
  }

  function startOver() {
    setFile(null);
    setReport(null);
    setError(null);
  }

  const good = report ? report.rows - report.failed : 0;
  const rows = report?.results.filter((row) => !onlyProblems || row.outcome === "FAILED") ?? [];

  return (
    <div className="grid max-w-4xl gap-6">
      <p className="text-muted-foreground">{t("intro")}</p>
      <Button
        variant="secondary"
        className="justify-self-start"
        onClick={() => void downloadTemplate()}
      >
        <Download aria-hidden />
        {t("template")}
      </Button>

      {error && <FormAlert>{error}</FormAlert>}

      {(!report || report.dryRun) && (
        <div className="grid gap-3">
          <FormField label={t("file")}>
            {(field) => (
              <FileUpload
                {...field}
                accept=".csv,text/csv"
                value={file}
                disabled={busy}
                onChange={(chosen) => {
                  setFile(chosen);
                  setReport(null);
                }}
              />
            )}
          </FormField>
          {progress !== null && <UploadProgress value={progress} />}
          {!report && (
            <Button
              className="justify-self-start"
              disabled={!file || busy}
              onClick={() => void run(true)}
            >
              {busy ? t("checking") : t("check")}
            </Button>
          )}
        </div>
      )}

      {report && (
        <section className="grid gap-3" aria-labelledby="import-report">
          <h2 id="import-report" className="font-medium">
            {report.dryRun ? t("previewTitle") : t("resultTitle")}
          </h2>
          <p role="status">
            {t("summary", {
              rows: report.rows,
              created: report.created,
              copies: report.copiesAdded,
              failed: report.failed,
            })}
          </p>
          {report.dryRun && report.failed > 0 && (
            <p className="text-sm text-muted-foreground">{t("fixHint")}</p>
          )}

          <div className="flex flex-wrap items-center gap-3">
            {report.dryRun && good > 0 && (
              <Button disabled={busy} onClick={() => void run(false)}>
                {busy ? t("importing") : t("confirm", { count: good })}
              </Button>
            )}
            {!report.dryRun && (
              <Button variant="secondary" onClick={startOver}>
                {t("startOver")}
              </Button>
            )}
            {report.failed > 0 && (
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  className="accent-primary"
                  checked={onlyProblems}
                  onChange={(event) => setOnlyProblems(event.target.checked)}
                />
                {t("onlyProblems")}
              </label>
            )}
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-16">{t("line")}</TableHead>
                <TableHead>{t("outcome")}</TableHead>
                <TableHead>{t("bookTitle")}</TableHead>
                <TableHead className="text-right">{t("copies")}</TableHead>
                <TableHead>{t("problem")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.line}>
                  <TableCell className="font-mono">{row.line}</TableCell>
                  <TableCell>
                    <Badge variant={OUTCOME_VARIANT[row.outcome]}>
                      {t(`outcomes.${row.outcome}`)}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {row.bookId && !report.dryRun ? (
                      <Link
                        href={`/staff/catalog/${row.bookId}`}
                        className="underline-offset-4 hover:underline"
                      >
                        {row.title}
                      </Link>
                    ) : (
                      (row.title ?? "–")
                    )}
                  </TableCell>
                  <TableCell className="text-right">{row.copiesAdded}</TableCell>
                  <TableCell className="text-sm text-destructive">{row.error}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </section>
      )}
    </div>
  );
}
