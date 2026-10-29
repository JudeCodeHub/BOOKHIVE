package lk.bookhive.report.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.report.service.BookUsageReportService;
import lk.bookhive.report.service.CirculationReportService;
import lk.bookhive.report.service.CirculationReportService.CirculationReport;
import lk.bookhive.report.service.CirculationReportService.GroupBy;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports for admins (RPT-2...RPT-5, checklist steps 361-364). Dates are library days in Sri Lanka,
 * both included. CSV and PDF downloads come in step 366.
 */
@RestController
@RequestMapping("/admin/reports")
@Tag(name = "Admin: reports")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminReportController {

  /**
   * @param borrowers different members (counted once for the whole range)
   * @param uncategorised loans of books with no category (left out of the category rows)
   */
  public record CirculationTotalsSummary(
      long checkouts, long returns, long borrowers, long uncategorised) {}

  /**
   * @param start the day, or the first day of the month
   */
  public record CirculationPeriodSummary(
      LocalDate start, long checkouts, long returns, long borrowers) {}

  public record CirculationCategorySummary(
      UUID categoryId,
      String nameEn,
      String nameSi,
      String nameTa,
      @Schema(nullable = true) String ddcCode,
      long checkouts,
      long borrowers) {}

  /**
   * @param periods filled for DAY and MONTH: every period in the range, oldest first
   * @param categories filled for CATEGORY: top-level categories, most borrowed first; a book in two
   *     categories counts in both
   */
  public record CirculationReportResponse(
      LocalDate from,
      LocalDate to,
      GroupBy groupBy,
      CirculationTotalsSummary totals,
      List<CirculationPeriodSummary> periods,
      List<CirculationCategorySummary> categories) {

    static CirculationReportResponse from(CirculationReport r) {
      var t = r.totals();
      return new CirculationReportResponse(
          r.from(),
          r.to(),
          r.groupBy(),
          new CirculationTotalsSummary(
              t.checkouts(), t.returns(), t.borrowers(), t.uncategorised()),
          r.periods().stream()
              .map(
                  p ->
                      new CirculationPeriodSummary(
                          p.start(), p.checkouts(), p.returns(), p.borrowers()))
              .toList(),
          r.categories().stream()
              .map(
                  c ->
                      new CirculationCategorySummary(
                          c.categoryId(),
                          c.nameEn(),
                          c.nameSi(),
                          c.nameTa(),
                          c.ddcCode(),
                          c.checkouts(),
                          c.borrowers()))
              .toList());
    }
  }

  /**
   * @param authors in their order, comma separated
   * @param copies copies in stock (not lost or withdrawn)
   * @param borrowers different members
   */
  public record MostBorrowedSummary(
      UUID bookId,
      String title,
      @Schema(nullable = true) String authors,
      @Schema(nullable = true) String callNumber,
      long copies,
      long checkouts,
      long borrowers) {}

  /**
   * @param copies lendable copies on the shelf
   * @param inStockSince when the oldest of them was added
   * @param lastBorrowedOn null = never borrowed
   */
  public record NotBorrowedSummary(
      UUID bookId,
      String title,
      @Schema(nullable = true) String authors,
      @Schema(nullable = true) String callNumber,
      long copies,
      LocalDate inStockSince,
      @Schema(nullable = true) LocalDate lastBorrowedOn) {}

  /**
   * @param since books not borrowed since this day (and on the shelf from before it)
   */
  public record NotBorrowedReportResponse(
      LocalDate since,
      List<NotBorrowedSummary> books,
      int page,
      int size,
      long totalElements,
      int totalPages) {}

  private final CirculationReportService circulation;
  private final BookUsageReportService usage;

  public AdminReportController(CirculationReportService circulation, BookUsageReportService usage) {
    this.circulation = circulation;
    this.usage = usage;
  }

  @GetMapping("/circulation")
  @Operation(
      summary =
          "Loans and returns per day (up to 366 days), per month, or per top-level category"
              + " (up to 5 years)")
  public CirculationReportResponse circulation(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "DAY") GroupBy groupBy) {
    return CirculationReportResponse.from(circulation.report(from, to, groupBy));
  }

  @GetMapping("/most-borrowed")
  @Operation(summary = "The most borrowed titles between two days, optionally in one category")
  public List<MostBorrowedSummary> mostBorrowed(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
    return usage.mostBorrowed(from, to, categoryId, limit).stream()
        .map(
            r ->
                new MostBorrowedSummary(
                    r.bookId(),
                    r.title(),
                    r.authors(),
                    r.callNumber(),
                    r.copies(),
                    r.checkouts(),
                    r.borrowers()))
        .toList();
  }

  @GetMapping("/not-borrowed")
  @Operation(
      summary =
          "Weeding list: lendable books on the shelf since before `since` (default 2 years ago)"
              + " that nobody borrowed since; never-borrowed first, then in shelf order")
  public NotBorrowedReportResponse notBorrowed(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate since,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    var result = usage.notBorrowedSince(since, categoryId, page, size);
    return new NotBorrowedReportResponse(
        result.since(),
        result.books().stream()
            .map(
                r ->
                    new NotBorrowedSummary(
                        r.bookId(),
                        r.title(),
                        r.authors(),
                        r.callNumber(),
                        r.copies(),
                        r.inStockSince(),
                        r.lastBorrowedOn()))
            .toList(),
        page,
        size,
        result.total(),
        (int) ((result.total() + size - 1) / size));
  }
}
