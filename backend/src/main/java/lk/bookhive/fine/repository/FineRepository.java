package lk.bookhive.fine.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.fine.domain.Fine;
import lk.bookhive.fine.domain.FineStatus;
import lk.bookhive.fine.domain.FineType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FineRepository extends JpaRepository<Fine, UUID> {

  /** The fine of this type for a loan (one overdue and one lost fine per loan at most). */
  Optional<Fine> findFirstByLoanIdAndType(UUID loanId, FineType type);

  /** A member's fines in these statuses, oldest first (fines page, paying oldest first). */
  List<Fine> findByMemberIdAndStatusInOrderByCreatedAt(
      UUID memberId, Collection<FineStatus> statuses);

  /** Daily accrual job: overdue fines still growing. */
  List<Fine> findByAccruingTrue();

  /**
   * What the member owes now: the ledger balance of their open fines (FIN-4 borrowing block).
   * Charges and refunds add, payments, waivers and cancellations subtract.
   */
  @Query(
      value =
          """
          SELECT COALESCE(SUM(CASE WHEN t.kind IN ('CHARGE', 'REFUND') THEN t.amount
                                   ELSE -t.amount END), 0)
          FROM fine_transactions t JOIN fines f ON f.id = t.fine_id
          WHERE f.member_id = :memberId AND f.status = 'OPEN'
          """,
      nativeQuery = true)
  BigDecimal outstandingFor(@Param("memberId") UUID memberId);
}
