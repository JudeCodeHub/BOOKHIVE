package lk.bookhive.report.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.report.repository.CirculationReportQueries.Period;
import lk.bookhive.report.service.BookUsageReportService;
import lk.bookhive.report.service.CirculationReportService;
import lk.bookhive.report.service.CirculationReportService.CirculationReport;
import lk.bookhive.report.service.CirculationReportService.GroupBy;
import lk.bookhive.report.service.OverdueFineReportService;
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

  /**
   * @param daysOverdue days past the due date today
   * @param fineOwed still owed on this loan's overdue fine (LKR)
   */
  public record OverdueLoanSummary(
      UUID loanId,
      String memberNo,
      String memberName,
      String email,
      @Schema(nullable = true) String phone,
      String title,
      String barcode,
      LocalDate dueDate,
      long daysOverdue,
      BigDecimal fineOwed) {}

  /**
   * @param upToWeek 1-7 days late
   * @param upToMonth 8-30 days late
   * @param overMonth more than 30 days late
   * @param finesOwed still owed on all of them (LKR)
   */
  public record OverdueTotalsSummary(
      long total, long upToWeek, long upToMonth, long overMonth, BigDecimal finesOwed) {}

  /**
   * @param loans most overdue first
   */
  public record OverdueReportResponse(
      LocalDate today,
      OverdueTotalsSummary totals,
      List<OverdueLoanSummary> loans,
      int page,
      int size,
      int totalPages) {}

  /** Amounts in LKR; collected = cash + online - refunded. */
  public record FineTotalsSummary(
      BigDecimal charged,
      BigDecimal collectedCash,
      BigDecimal collectedOnline,
      BigDecimal refunded,
      BigDecimal collected,
      BigDecimal waived,
      BigDecimal cancelled) {}

  public record FinePeriodSummary(
      LocalDate start,
      BigDecimal charged,
      BigDecimal collectedCash,
      BigDecimal collectedOnline,
      BigDecimal refunded,
      BigDecimal waived,
      BigDecimal cancelled) {}

  /**
   * @param type OVERDUE, LOST, DAMAGE or MEMBERSHIP_FEE
   */
  public record FineTypeSummary(
      String type, BigDecimal charged, BigDecimal collected, BigDecimal waived) {}

  /**
   * @param periods every day or month in the range, oldest first
   * @param byType every kind of fine, most charged first
   */
  public record FineReportResponse(
      LocalDate from,
      LocalDate to,
      Period groupBy,
      FineTotalsSummary totals,
      List<FinePeriodSummary> periods,
      List<FineTypeSummary> byType) {}

  private final CirculationReportService circulation;
  private final BookUsageReportService usage;
  private final OverdueFineReportService money;

  public AdminReportController(
      CirculationReportService circulation,
      BookUsageReportService usage,
      OverdueFineReportService money) {
    this.circulation = circulation;
    this.usage = usage;
    this.money = money;
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

  @GetMapping("/overdue")
  @Operation(
      summary =
          "Books still out past their due date, most overdue first, with the member's contact"
              + " details and what's owed")
  public OverdueReportResponse overdue(
      @RequestParam(defaultValue = "1") @Min(1) @Max(3650) int minDays,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    var result = money.overdue(minDays, page, size);
    var s = result.summary();
    return new OverdueReportResponse(
        result.today(),
        new OverdueTotalsSummary(
            s.total(), s.upToWeek(), s.upToMonth(), s.overMonth(), s.finesOwed()),
        result.loans().stream()
            .map(
                r ->
                    new OverdueLoanSummary(
                        r.loanId(),
                        r.memberNo(),
                        r.memberName(),
                        r.email(),
                        r.phone(),
                        r.title(),
                        r.barcode(),
                        r.dueDate(),
                        r.daysOverdue(),
                        r.fineOwed()))
            .toList(),
        page,
        size,
        (int) ((s.total() + size - 1) / size));
  }

  @GetMapping("/fines")
  @Operation(
      summary =
          "Money from fines per day (up to 366 days) or month: charged, collected (cash/online),"
              + " refunded, waived, cancelled; and per kind of fine")
  public FineReportResponse fines(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "MONTH") Period groupBy) {
    var r = money.fines(from, to, groupBy);
    var t = r.totals();
    return new FineReportResponse(
        r.from(),
        r.to(),
        r.groupBy(),
        new FineTotalsSummary(
            t.charged(),
            t.collectedCash(),
            t.collectedOnline(),
            t.refunded(),
            t.collected(),
            t.waived(),
            t.cancelled()),
        r.periods().stream()
            .map(
                p ->
                    new FinePeriodSummary(
                        p.start(),
                        p.charged(),
                        p.collectedCash(),
                        p.collectedOnline(),
                        p.refunded(),
                        p.waived(),
                        p.cancelled()))
            .toList(),
        r.byType().stream()
            .map(x -> new FineTypeSummary(x.type(), x.charged(), x.collected(), x.waived()))
            .toList());
  }
}
