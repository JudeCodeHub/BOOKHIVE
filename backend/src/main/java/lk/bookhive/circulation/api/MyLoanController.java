package lk.bookhive.circulation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lk.bookhive.circulation.api.LoanViews.LoanSummary;
import lk.bookhive.circulation.api.RenewalResponses.LoanRenewalResponse;
import lk.bookhive.circulation.api.RenewalResponses.RenewalCheckResponse;
import lk.bookhive.circulation.service.LoanHistoryService;
import lk.bookhive.circulation.service.LoanHistoryService.ReadingHistory;
import lk.bookhive.circulation.service.RenewalService;
import lk.bookhive.circulation.service.RenewalService.RenewalRefusedException;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.member.service.BorrowerService;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in member's own loans: current, reading history (231) and renewing (227). */
@RestController
@RequestMapping("/me/loans")
@Tag(name = "My loans")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MyLoanController {

  private final RenewalService renewals;
  private final LoanHistoryService history;
  private final BorrowerService borrowers;

  public MyLoanController(
      RenewalService renewals, LoanHistoryService history, BorrowerService borrowers) {
    this.renewals = renewals;
    this.history = history;
    this.borrowers = borrowers;
  }

  /**
   * @param historyEnabled false when I switched reading history off (then the list is empty)
   * @param anonymiseAfterDays when it's off, returned loans are anonymised after this many days
   */
  public record ReadingHistoryResponse(
      boolean historyEnabled, int anonymiseAfterDays, PageResponse<LoanSummary> loans) {}

  @GetMapping
  @Operation(summary = "My current loans (out or lost), soonest due first")
  public List<LoanSummary> current(@AuthenticationPrincipal Jwt jwt) {
    return history.current(memberId(jwt)).stream().map(LoanSummary::from).toList();
  }

  @GetMapping("/history")
  @Operation(summary = "My reading history: returned loans, newest first")
  public ReadingHistoryResponse readingHistory(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    ReadingHistory result = history.readingHistory(memberId(jwt), page, size);
    return new ReadingHistoryResponse(
        result.historyEnabled(),
        result.anonymiseAfterDays(),
        PageResponse.from(result.loans(), LoanSummary::from));
  }

  @GetMapping("/{loanId}/renewal")
  @Operation(summary = "Can I renew this loan now, and if not, why")
  public RenewalCheckResponse check(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID loanId) {
    return RenewalCheckResponse.from(renewals.checkOwn(loanId, memberId(jwt)));
  }

  @PostMapping("/{loanId}/renew")
  @Operation(summary = "Renew my loan (422 RENEWAL_REFUSED lists every reason)")
  public LoanRenewalResponse renew(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID loanId) {
    return LoanRenewalResponse.from(
        renewals.renewOwn(loanId, memberId(jwt), UUID.fromString(jwt.getSubject())));
  }

  @ExceptionHandler(RenewalRefusedException.class)
  public ProblemDetail refused(RenewalRefusedException ex) {
    return RenewalResponses.refused(ex);
  }

  private UUID memberId(Jwt jwt) {
    return borrowers.memberIdOfUser(UUID.fromString(jwt.getSubject()));
  }
}
