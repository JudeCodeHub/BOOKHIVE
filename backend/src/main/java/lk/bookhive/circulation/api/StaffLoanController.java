package lk.bookhive.circulation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lk.bookhive.circulation.api.LoanViews.OpenLoanSummary;
import lk.bookhive.circulation.api.RenewalResponses.LoanRenewalResponse;
import lk.bookhive.circulation.api.RenewalResponses.RenewalCheckResponse;
import lk.bookhive.circulation.domain.LoanStatus;
import lk.bookhive.circulation.service.LoanHistoryService;
import lk.bookhive.circulation.service.LossService;
import lk.bookhive.circulation.service.LossService.Charge;
import lk.bookhive.circulation.service.RenewalService;
import lk.bookhive.circulation.service.RenewalService.RenewalRefusedException;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.domain.Money;
import lk.bookhive.common.web.PageResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Loans at the desk (librarians and admins): the lists of current and overdue loans (step 232),
 * renewing, lost and damaged (steps 227-229).
 */
@RestController
@RequestMapping("/staff/loans")
@Tag(name = "Staff: circulation")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffLoanController {

  private final RenewalService renewals;
  private final LossService losses;
  private final LoanHistoryService history;

  public StaffLoanController(
      RenewalService renewals, LossService losses, LoanHistoryService history) {
    this.renewals = renewals;
    this.losses = losses;
    this.history = history;
  }

  @GetMapping
  @Operation(
      summary =
          "Loans out now, soonest due first; overdue=true: only overdue, longest first;"
              + " dueWithinDays=N: only those due in the next N days")
  public PageResponse<OpenLoanSummary> list(
      @RequestParam(defaultValue = "false") boolean overdue,
      @RequestParam(required = false) @Min(0) @Max(60) Integer dueWithinDays,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(
        history.openLoans(overdue, dueWithinDays, page, size), OpenLoanSummary::from);
  }

  /**
   * @param amount LKR, more than 0 and at most the copy's replacement cost
   * @param note what's wrong with it
   */
  public record DamageRequest(
      @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal amount,
      @NotBlank @Size(max = 500) String note) {}

  /**
   * What the member is charged (recorded on their account by the fine module).
   *
   * @param processingFee lost items only (0 for damage)
   * @param total amount + processingFee
   */
  public record LoanChargeResponse(
      UUID loanId,
      String barcode,
      String title,
      LoanStatus loanStatus,
      BigDecimal amount,
      BigDecimal processingFee,
      BigDecimal total) {

    static LoanChargeResponse from(Charge charge) {
      return new LoanChargeResponse(
          charge.loan().getId(),
          charge.copy().barcode(),
          charge.copy().title(),
          charge.loan().getStatus(),
          charge.amount().amount(),
          charge.processingFee().amount(),
          charge.total().amount());
    }
  }

  /**
   * @param overrideReason admins only: renew despite overridable problems (audited)
   */
  public record RenewRequest(@Size(min = 10, max = 500) String overrideReason) {}

  @GetMapping("/{loanId}/renewal")
  @Operation(summary = "Can this loan be renewed now, and if not, why")
  public RenewalCheckResponse check(@PathVariable UUID loanId) {
    return RenewalCheckResponse.from(renewals.check(loanId));
  }

  @PostMapping("/{loanId}/renew")
  @Operation(
      summary =
          "Renew a loan (422 RENEWAL_REFUSED lists every reason; admins may override with a"
              + " reason)")
  public LoanRenewalResponse renew(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID loanId,
      @Valid @RequestBody(required = false) RenewRequest request) {
    String reason = request == null ? null : request.overrideReason();
    return LoanRenewalResponse.from(
        renewals.renew(
            loanId,
            UUID.fromString(jwt.getSubject()),
            StaffCheckoutController.blankToNull(reason)));
  }

  @PostMapping("/{loanId}/lost")
  @Operation(summary = "Mark a loan lost: charges replacement cost + processing fee")
  public LoanChargeResponse lost(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID loanId) {
    return LoanChargeResponse.from(losses.markLost(loanId, UUID.fromString(jwt.getSubject())));
  }

  @PostMapping("/{loanId}/damage")
  @Operation(summary = "Charge for damage on this loan (up to the replacement cost)")
  public LoanChargeResponse damaged(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID loanId,
      @Valid @RequestBody DamageRequest request) {
    return LoanChargeResponse.from(
        losses.markDamaged(
            loanId,
            new Money(request.amount()),
            request.note(),
            UUID.fromString(jwt.getSubject())));
  }

  @ExceptionHandler(RenewalRefusedException.class)
  public ProblemDetail refused(RenewalRefusedException ex) {
    return RenewalResponses.refused(ex);
  }
}
