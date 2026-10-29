package lk.bookhive.circulation.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.circulation.repository.LoanRepository;
import lk.bookhive.common.time.LibraryTime;
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

  /**
   * "Readers also borrowed" (step 339): other books borrowed by this book's readers, most shared
   * first, only when at least {@code minReaders} different readers did (privacy).
   */
  @Transactional(readOnly = true)
  public List<UUID> alsoBorrowed(UUID bookId, int minReaders, int limit) {
    return loans.alsoBorrowed(bookId, minReaders, limit).stream()
        .map(LoanRepository.CoBorrow::getBookId)
        .toList();
  }

  /**
   * Today at the desk (staff dashboard, step 360).
   *
   * @param overdue open loans past their due date (whether or not the nightly job ran yet)
   */
  public record DeskDay(long checkedOut, long returned, long dueToday, long overdue) {}

  /** Loans and returns on one library day. */
  public record DayActivity(LocalDate day, long checkouts, long returns) {}

  /** {@code start}/{@code end} bound today in library time. */
  @Transactional(readOnly = true)
  public DeskDay deskDay(Instant start, Instant end, LocalDate today) {
    LoanRepository.DeskCounts c = loans.deskCounts(start, end, today);
    return new DeskDay(c.getCheckedOut(), c.getReturned(), c.getDueToday(), c.getOverdue());
  }

  /** Loans and returns per day from {@code from} to {@code to} (library days, both included). */
  @Transactional(readOnly = true)
  public List<DayActivity> dailyActivity(LocalDate from, LocalDate to) {
    return loans
        .dailyActivity(
            from, to, from.atStartOfDay(LibraryTime.ZONE).toInstant(), LibraryTime.ZONE.getId())
        .stream()
        .map(d -> new DayActivity(LocalDate.parse(d.getDay()), d.getCheckouts(), d.getReturns()))
        .toList();
  }

  /** Has the member got a copy of this title out now (on time or overdue)? */
  @Transactional(readOnly = true)
  public boolean hasBookOut(UUID memberId, UUID bookId) {
    return loans.memberHasBookOut(memberId, bookId);
  }
}
