package lk.bookhive.circulation.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.circulation.repository.LoanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Questions other modules ask about loans (e.g. the hold rules, step 251). */
@Service
public class LoanLookupService {

  private final LoanRepository loans;

  public LoanLookupService(LoanRepository loans) {
    this.loans = loans;
  }

  /** An open loan past its due date, for the fine module's daily accrual (step 271). */
  public record OverdueLoan(UUID loanId, UUID memberId, UUID copyId, LocalDate dueDate) {}

  /** Every open loan whose due date is before {@code today} (whatever the nightly status). */
  @Transactional(readOnly = true)
  public List<OverdueLoan> overdueOn(LocalDate today) {
    return loans.findByStatusInAndDueDateBefore(LoanRepository.OPEN, today).stream()
        .map(l -> new OverdueLoan(l.getId(), l.getMemberId(), l.getCopyId(), l.getDueDate()))
        .toList();
  }

  /**
   * Has the member ever borrowed this title (reviews, step 337)? Loans anonymised because the
   * member switched reading history off no longer count.
   */
  @Transactional(readOnly = true)
  public boolean hasEverBorrowed(UUID memberId, UUID bookId) {
    return loans.memberEverBorrowed(memberId, bookId);
  }

  /** Has the member got a copy of this title out now (on time or overdue)? */
  @Transactional(readOnly = true)
  public boolean hasBookOut(UUID memberId, UUID bookId) {
    return loans.memberHasBookOut(memberId, bookId);
  }
}
