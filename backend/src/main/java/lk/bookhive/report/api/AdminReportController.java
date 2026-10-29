package lk.bookhive.report.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
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

  private final CirculationReportService circulation;

  public AdminReportController(CirculationReportService circulation) {
    this.circulation = circulation;
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
}
