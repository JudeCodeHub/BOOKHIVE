package lk.bookhive.circulation.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lk.bookhive.circulation.domain.LoanStatus;
import lk.bookhive.circulation.service.LoanHistoryService.CopyLoan;
import lk.bookhive.circulation.service.LoanHistoryService.LoanView;
import lk.bookhive.circulation.service.LoanHistoryService.OpenLoan;

/** Loan list rows shared by the member and staff endpoints (step 231). */
public final class LoanViews {

  private LoanViews() {}

  /**
   * One loan with its book.
   *
   * @param overdue past the due date today
   * @param returnedAt null while it's out
   */
  public record LoanSummary(
      UUID loanId,
      UUID copyId,
      UUID bookId,
      String barcode,
      String title,
      String coverVersion,
      Instant checkedOutAt,
      LocalDate dueDate,
      int renewCount,
      LoanStatus status,
      boolean overdue,
      Instant returnedAt) {

    static LoanSummary from(LoanView view) {
      var loan = view.loan();
      var copy = view.copy();
      return new LoanSummary(
          loan.getId(),
          loan.getCopyId(),
          copy == null ? null : copy.bookId(),
          copy == null ? null : copy.barcode(),
          copy == null ? null : copy.title(),
          copy == null ? null : copy.coverVersion(),
          loan.getCheckedOutAt(),
          loan.getDueDate(),
          loan.getRenewCount(),
          loan.getStatus(),
          view.overdue(),
          loan.getReturnedAt());
    }
  }

  /**
   * A loan in the staff lists (step 232).
   *
   * @param daysOverdue 0 when not overdue
   */
  public record OpenLoanSummary(
      LoanSummary loan,
      UUID memberId,
      String memberNo,
      String fullName,
      String phone,
      long daysOverdue) {

    static OpenLoanSummary from(OpenLoan row) {
      return new OpenLoanSummary(
          LoanSummary.from(row.view()),
          row.view().loan().getMemberId(),
          row.borrower() == null ? null : row.borrower().memberNo(),
          row.fullName(),
          row.phone(),
          row.daysOverdue());
    }
  }

  /**
   * A copy's loan, for staff.
   *
   * @param memberNo who has it now; null once it's returned (reading history is private)
   */
  public record CopyLoanSummary(
      UUID loanId,
      Instant checkedOutAt,
      LocalDate dueDate,
      int renewCount,
      LoanStatus status,
      Instant returnedAt,
      String memberNo) {

    static CopyLoanSummary from(CopyLoan row) {
      var loan = row.loan();
      return new CopyLoanSummary(
          loan.getId(),
          loan.getCheckedOutAt(),
          loan.getDueDate(),
          loan.getRenewCount(),
          loan.getStatus(),
          loan.getReturnedAt(),
          row.memberNo());
    }
  }
}
