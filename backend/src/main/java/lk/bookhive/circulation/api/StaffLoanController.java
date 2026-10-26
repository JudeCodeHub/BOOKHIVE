package lk.bookhive.circulation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lk.bookhive.circulation.api.RenewalResponses.LoanRenewalResponse;
import lk.bookhive.circulation.api.RenewalResponses.RenewalCheckResponse;
import lk.bookhive.circulation.service.RenewalService;
import lk.bookhive.circulation.service.RenewalService.RenewalRefusedException;
import lk.bookhive.common.config.OpenApiConfig;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Loans at the desk (librarians and admins): renewing (steps 227-228). */
@RestController
@RequestMapping("/staff/loans")
@Tag(name = "Staff: circulation")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffLoanController {

  private final RenewalService renewals;

  public StaffLoanController(RenewalService renewals) {
    this.renewals = renewals;
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

  @ExceptionHandler(RenewalRefusedException.class)
  public ProblemDetail refused(RenewalRefusedException ex) {
    return RenewalResponses.refused(ex);
  }
}
