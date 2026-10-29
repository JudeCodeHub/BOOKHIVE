package lk.bookhive.report.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.domain.Language;
import lk.bookhive.report.export.CsvExporter;
import lk.bookhive.report.export.PdfExporter;
import lk.bookhive.report.export.ReportDocument;
import lk.bookhive.report.export.ReportExportService;
import lk.bookhive.report.repository.CirculationReportQueries.Period;
import lk.bookhive.report.service.CirculationReportService.GroupBy;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report downloads (RPT-7, checklist step 366): every report as CSV (for spreadsheets) or PDF (to
 * print or send), with headings in English, Sinhala or Tamil. Same filters as the report itself.
 * Downloads are audited: some contain members' contact details.
 */
@RestController
@RequestMapping("/admin/reports")
@Tag(name = "Admin: reports")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminReportExportController {

  public enum Format {
    CSV,
    PDF
  }

  private static final String LANG = "^(en|si|ta)$";

  private final ReportExportService reports;
  private final CsvExporter csv;
  private final PdfExporter pdf;
  private final AuditService audit;

  public AdminReportExportController(
      ReportExportService reports, CsvExporter csv, PdfExporter pdf, AuditService audit) {
    this.reports = reports;
    this.csv = csv;
    this.pdf = pdf;
    this.audit = audit;
  }

  @GetMapping("/circulation/export")
  @Operation(summary = "Download the circulation report as CSV or PDF")
  public ResponseEntity<byte[]> circulation(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "DAY") GroupBy groupBy,
      @RequestParam(defaultValue = "CSV") Format format,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang) {
    return send(jwt, "circulation", format, reports.circulation(from, to, groupBy, language(lang)));
  }

  @GetMapping("/most-borrowed/export")
  @Operation(summary = "Download the most borrowed list as CSV or PDF")
  public ResponseEntity<byte[]> mostBorrowed(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "CSV") Format format,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang) {
    return send(
        jwt,
        "most-borrowed",
        format,
        reports.mostBorrowed(from, to, categoryId, limit, language(lang)));
  }

  @GetMapping("/not-borrowed/export")
  @Operation(summary = "Download the weeding list (not borrowed since) as CSV or PDF")
  public ResponseEntity<byte[]> notBorrowed(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate since,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(defaultValue = "CSV") Format format,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang) {
    return send(
        jwt, "not-borrowed", format, reports.notBorrowed(since, categoryId, language(lang)));
  }

  @GetMapping("/overdue/export")
  @Operation(summary = "Download the overdue list (with members' contact details) as CSV or PDF")
  public ResponseEntity<byte[]> overdue(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "1") @Min(1) @Max(3650) int minDays,
      @RequestParam(defaultValue = "CSV") Format format,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang) {
    return send(jwt, "overdue", format, reports.overdue(minDays, language(lang)));
  }

  @GetMapping("/fines/export")
  @Operation(summary = "Download the fines report as CSV or PDF")
  public ResponseEntity<byte[]> fines(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "MONTH") Period groupBy,
      @RequestParam(defaultValue = "CSV") Format format,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang) {
    return send(jwt, "fines", format, reports.fines(from, to, groupBy, language(lang)));
  }

  @GetMapping("/membership/export")
  @Operation(summary = "Download the membership growth report as CSV or PDF")
  public ResponseEntity<byte[]> membership(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "MONTH") Period groupBy,
      @RequestParam(defaultValue = "CSV") Format format,
      @RequestParam(defaultValue = "en") @Pattern(regexp = LANG) String lang) {
    return send(jwt, "membership", format, reports.membership(from, to, groupBy, language(lang)));
  }

  private ResponseEntity<byte[]> send(Jwt jwt, String report, Format format, ReportDocument doc) {
    boolean isPdf = format == Format.PDF;
    byte[] body = isPdf ? pdf.write(doc) : csv.write(doc);
    audit.record(
        "report.exported",
        UUID.fromString(jwt.getSubject()),
        "report",
        report,
        Map.of("format", format.name(), "file", doc.fileName()));
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(doc.fileName() + (isPdf ? ".pdf" : ".csv"), StandardCharsets.UTF_8)
                .build()
                .toString())
        .cacheControl(CacheControl.noStore())
        .contentType(
            isPdf
                ? MediaType.APPLICATION_PDF
                : new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(body);
  }

  private static Language language(String code) {
    return Language.fromCode(code);
  }
}
