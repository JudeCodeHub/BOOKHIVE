package lk.bookhive.report.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.report.domain.ScanResult;
import lk.bookhive.report.domain.StocktakeStatus;
import lk.bookhive.report.service.StocktakeService;
import lk.bookhive.report.service.StocktakeService.Counts;
import lk.bookhive.report.service.StocktakeService.IssueView;
import lk.bookhive.report.service.StocktakeService.ScanOutcome;
import lk.bookhive.report.service.StocktakeService.SessionView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stock-take for staff (RPT-6, checklist step 365): start, scan the shelves, see what's missing and
 * what needs a look, then complete or cancel.
 */
@RestController
@RequestMapping("/staff/stocktakes")
@Tag(name = "Staff: stock-take")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffStocktakeController {

  /**
   * @param categoryId this category and everything under it; null = any
   * @param shelfPrefix shelves whose location is this or starts with it as whole words, e.g.
   *     "Non-fiction 500s" (all its shelves) or "Non-fiction 500s, shelf 1" (not shelf 11); null =
   *     any
   */
  public record StartStocktakeRequest(
      @NotBlank @Size(max = 120) String name,
      UUID categoryId,
      @Size(max = 100) String shelfPrefix) {}

  public record ScanRequest(@NotBlank @Size(max = 30) String barcode) {}

  /** See StocktakeService.Counts. */
  public record StocktakeCountsSummary(
      long expected, long found, long missing, long scanned, long needsLook) {

    static StocktakeCountsSummary from(Counts c) {
      return new StocktakeCountsSummary(
          c.expected(), c.found(), c.missing(), c.scanned(), c.needsLook());
    }
  }

  /**
   * @param counts live while OPEN, frozen when COMPLETED
   */
  public record StocktakeSummary(
      UUID id,
      String name,
      @Schema(nullable = true) UUID categoryId,
      @Schema(nullable = true) String shelfPrefix,
      StocktakeStatus status,
      Instant startedAt,
      @Schema(nullable = true) Instant finishedAt,
      StocktakeCountsSummary counts) {

    static StocktakeSummary from(SessionView v) {
      var s = v.session();
      return new StocktakeSummary(
          s.getId(),
          s.getName(),
          s.getCategoryId(),
          s.getShelfPrefix(),
          s.getStatus(),
          s.getStartedAt(),
          s.getFinishedAt(),
          StocktakeCountsSummary.from(v.counts()));
    }
  }

  /**
   * @param result FOUND, NOT_IN_SCOPE (other shelf), NOT_ON_SHELF (system says it's out/lost),
   *     UNKNOWN (no such barcode)
   * @param alreadyScanned scanned before in this session (not counted twice)
   */
  public record ScanResultResponse(
      String barcode,
      ScanResult result,
      boolean alreadyScanned,
      @Schema(nullable = true) UUID copyId,
      @Schema(nullable = true) String title,
      @Schema(nullable = true) String shelfLocation,
      @Schema(nullable = true) String copyStatus,
      StocktakeCountsSummary counts) {

    static ScanResultResponse from(ScanOutcome o) {
      return new ScanResultResponse(
          o.barcode(),
          o.result(),
          o.alreadyScanned(),
          o.copyId(),
          o.title(),
          o.shelfLocation(),
          o.copyStatus(),
          StocktakeCountsSummary.from(o.counts()));
    }
  }

  public record MissingCopySummary(
      UUID copyId,
      String barcode,
      UUID bookId,
      String title,
      @Schema(nullable = true) String callNumber,
      @Schema(nullable = true) String shelfLocation) {}

  public record StocktakeIssueSummary(
      UUID scanId,
      String barcode,
      ScanResult result,
      @Schema(nullable = true) UUID copyId,
      @Schema(nullable = true) String copyStatus,
      @Schema(nullable = true) String title,
      @Schema(nullable = true) String shelfLocation,
      Instant scannedAt) {

    static StocktakeIssueSummary from(IssueView v) {
      var s = v.scan();
      return new StocktakeIssueSummary(
          s.getId(),
          s.getBarcode(),
          s.getResult(),
          s.getCopyId(),
          s.getCopyStatus(),
          v.title(),
          v.shelfLocation(),
          s.getScannedAt());
    }
  }

  private final StocktakeService stocktakes;

  public StaffStocktakeController(StocktakeService stocktakes) {
    this.stocktakes = stocktakes;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Start a stock-take for a category and/or shelves (both empty = everything)")
  public StocktakeSummary start(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartStocktakeRequest request) {
    return StocktakeSummary.from(
        stocktakes.start(
            request.name(), request.categoryId(), request.shelfPrefix(), staffId(jwt)));
  }

  @GetMapping
  @Operation(summary = "Stock-takes, newest first")
  public PageResponse<StocktakeSummary> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(stocktakes.list(page, size), StocktakeSummary::from);
  }

  @GetMapping("/{id}")
  @Operation(summary = "One stock-take with its counts")
  public StocktakeSummary get(@PathVariable UUID id) {
    return StocktakeSummary.from(stocktakes.get(id));
  }

  @PostMapping("/{id}/scans")
  @Operation(summary = "Scan one barcode: says at once whether it belongs here")
  public ScanResultResponse scan(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody ScanRequest request) {
    return ScanResultResponse.from(stocktakes.scan(id, request.barcode(), staffId(jwt)));
  }

  @GetMapping("/{id}/missing")
  @Operation(summary = "Copies that should be on the shelf but weren't scanned, in shelf order")
  public List<MissingCopySummary> missing(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
    return stocktakes.missing(id, page, size).stream()
        .map(
            m ->
                new MissingCopySummary(
                    m.copyId(),
                    m.barcode(),
                    m.bookId(),
                    m.title(),
                    m.callNumber(),
                    m.shelfLocation()))
        .toList();
  }

  @GetMapping("/{id}/issues")
  @Operation(summary = "Scans that need a look: other shelf, wrong status, unknown barcode")
  public List<StocktakeIssueSummary> issues(@PathVariable UUID id) {
    return stocktakes.issues(id).stream().map(StocktakeIssueSummary::from).toList();
  }

  @PostMapping("/{id}/complete")
  @Operation(summary = "Finish: the counts are kept as they are now")
  public StocktakeSummary complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return StocktakeSummary.from(stocktakes.complete(id, staffId(jwt)));
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "Stop without a result")
  public StocktakeSummary cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return StocktakeSummary.from(stocktakes.cancel(id, staffId(jwt)));
  }

  private static UUID staffId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
