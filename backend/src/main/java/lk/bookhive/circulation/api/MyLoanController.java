package lk.bookhive.circulation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lk.bookhive.circulation.api.RenewalResponses.LoanRenewalResponse;
import lk.bookhive.circulation.api.RenewalResponses.RenewalCheckResponse;
import lk.bookhive.circulation.service.RenewalService;
import lk.bookhive.circulation.service.RenewalService.RenewalRefusedException;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.member.service.BorrowerService;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in member's own loans: renewing (step 227). */
@RestController
@RequestMapping("/me/loans")
@Tag(name = "My loans")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MyLoanController {

  private final RenewalService renewals;
  private final BorrowerService borrowers;

  public MyLoanController(RenewalService renewals, BorrowerService borrowers) {
    this.renewals = renewals;
    this.borrowers = borrowers;
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
    return borrowers
        .byUserId(UUID.fromString(jwt.getSubject()))
        .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Loan not found."))
        .memberId();
  }
}
