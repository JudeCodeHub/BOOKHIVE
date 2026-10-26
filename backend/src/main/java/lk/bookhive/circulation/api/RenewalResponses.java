package lk.bookhive.circulation.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.circulation.service.RenewalService.RenewalProblem;
import lk.bookhive.circulation.service.RenewalService.RenewalReason;
import lk.bookhive.circulation.service.RenewalService.RenewalRefusedException;
import lk.bookhive.circulation.service.RenewalService.Renewed;
import org.springframework.http.ProblemDetail;

/** What the member and staff renew endpoints return (step 227). */
public final class RenewalResponses {

  private RenewalResponses() {}

  /**
   * @param overridable an admin may renew anyway with a reason (step 228)
   */
  public record RenewalProblemSummary(RenewalReason reason, boolean overridable, String message) {

    static List<RenewalProblemSummary> from(List<RenewalProblem> problems) {
      return problems.stream()
          .map(p -> new RenewalProblemSummary(p.reason(), p.overridable(), p.message()))
          .toList();
    }
  }

  /**
   * Can it be renewed now, and if not, why.
   *
   * @param canOverride not allowed, but only for reasons an admin may override
   */
  public record RenewalCheckResponse(
      boolean canRenew, boolean canOverride, List<RenewalProblemSummary> problems) {

    static RenewalCheckResponse from(List<RenewalProblem> problems) {
      return new RenewalCheckResponse(
          problems.isEmpty(),
          !problems.isEmpty() && problems.stream().allMatch(RenewalProblem::overridable),
          RenewalProblemSummary.from(problems));
    }
  }

  /**
   * @param dueDate the new due date
   * @param renewCount renewals used so far, including this one
   */
  public record LoanRenewalResponse(
      UUID loanId, String barcode, String title, LocalDate dueDate, int renewCount) {

    static LoanRenewalResponse from(Renewed renewed) {
      return new LoanRenewalResponse(
          renewed.loan().getId(),
          renewed.copy().barcode(),
          renewed.copy().title(),
          renewed.loan().getDueDate(),
          renewed.loan().getRenewCount());
    }
  }

  /** 422 RENEWAL_REFUSED with every reason in {@code renewal}. */
  static ProblemDetail refused(RenewalRefusedException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    problem.setProperty("code", ex.getCode());
    problem.setProperty("renewal", RenewalCheckResponse.from(ex.getProblems()));
    return problem;
  }
}
