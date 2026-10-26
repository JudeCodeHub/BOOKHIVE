package lk.bookhive.circulation.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.circulation.domain.Loan;
import lk.bookhive.circulation.domain.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

  /** Statuses of a loan that is still out. */
  List<LoanStatus> OPEN = List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE);

  /** The open loan of a copy, if it's out (returns at the desk: scan the copy). */
  Optional<Loan> findFirstByCopyIdAndStatusIn(UUID copyId, Collection<LoanStatus> statuses);

  /** A member's open loans, soonest due first ("My loans", the desk summary). */
  List<Loan> findByMemberIdAndStatusInOrderByDueDate(
      UUID memberId, Collection<LoanStatus> statuses);

  /** For the loan limit: how many items the member has out now. */
  long countByMemberIdAndStatusIn(UUID memberId, Collection<LoanStatus> statuses);

  /** Statuses a member still has to deal with: out, or lost and charged. */
  List<LoanStatus> CURRENT = List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.LOST);

  /** Reading history: a member's loans in one status (RETURNED), newest first. */
  Page<Loan> findByMemberIdAndStatusOrderByCheckedOutAtDesc(
      UUID memberId, LoanStatus status, Pageable pageable);

  /** A copy's loans, newest first (staff). */
  Page<Loan> findByCopyIdOrderByCheckedOutAtDesc(UUID copyId, Pageable pageable);

  /** Nightly job: open loans whose due date is before today and not yet marked overdue. */
  @Query(
      "SELECT l FROM Loan l WHERE l.status = lk.bookhive.circulation.domain.LoanStatus.ACTIVE"
          + " AND l.dueDate < :today")
  List<Loan> findBecomingOverdue(@Param("today") LocalDate today);

  /** Staff lists: open loans, optionally only overdue ones, soonest due first. */
  Page<Loan> findByStatusInOrderByDueDate(Collection<LoanStatus> statuses, Pageable pageable);
}
