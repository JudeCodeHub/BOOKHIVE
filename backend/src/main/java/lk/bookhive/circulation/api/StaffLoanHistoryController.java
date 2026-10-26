package lk.bookhive.circulation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lk.bookhive.circulation.api.LoanViews.CopyLoanSummary;
import lk.bookhive.circulation.api.LoanViews.LoanSummary;
import lk.bookhive.circulation.service.LoanHistoryService;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Loans of a member or a copy, for staff (step 231). A member's reading history is private (PRD
 * §4.5): staff get current loans only.
 */
@RestController
@RequestMapping("/staff")
@Tag(name = "Staff: circulation")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffLoanHistoryController {

  private final LoanHistoryService history;

  public StaffLoanHistoryController(LoanHistoryService history) {
    this.history = history;
  }

  @GetMapping("/members/{memberId}/loans")
  @Operation(summary = "A member's current loans (out or lost), soonest due first")
  public List<LoanSummary> memberLoans(@PathVariable UUID memberId) {
    return history.current(memberId).stream().map(LoanSummary::from).toList();
  }

  @GetMapping("/copies/{copyId}/loans")
  @Operation(summary = "A copy's loans, newest first (borrower shown only while it's out)")
  public PageResponse<CopyLoanSummary> copyLoans(
      @PathVariable UUID copyId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(history.forCopy(copyId, page, size), CopyLoanSummary::from);
  }
}
