package lk.bookhive.fine.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.bookhive.fine.domain.Fine;
import lk.bookhive.fine.domain.FineStatus;
import lk.bookhive.fine.domain.FineTransaction;
import lk.bookhive.fine.domain.FineTransactionKind;
import lk.bookhive.fine.domain.FineType;
import lk.bookhive.fine.domain.PaymentMethod;
import lk.bookhive.fine.service.FineQueryService.MemberFines;

/** What the member and staff fine endpoints return (step 274). Amounts are LKR. */
public final class FineResponses {

  private FineResponses() {}

  /** One ledger line. */
  public record FineLineSummary(
      FineTransactionKind kind,
      BigDecimal amount,
      PaymentMethod method,
      String reference,
      String reason,
      Instant createdAt) {

    static FineLineSummary from(FineTransaction tx) {
      return new FineLineSummary(
          tx.getKind(),
          tx.getAmount().amount(),
          tx.getMethod(),
          tx.getReference(),
          tx.getReason(),
          tx.getCreatedAt());
    }
  }

  /**
   * @param accruing an overdue fine still growing daily (the book isn't back yet)
   * @param charged everything charged so far
   * @param balance still owed (negative: the library owes the member a refund)
   * @param lines the ledger, oldest first
   */
  public record FineSummary(
      UUID fineId,
      UUID loanId,
      FineType type,
      String description,
      FineStatus status,
      boolean accruing,
      BigDecimal charged,
      BigDecimal balance,
      Instant createdAt,
      Instant settledAt,
      List<FineLineSummary> lines) {

    static FineSummary from(Fine fine) {
      return new FineSummary(
          fine.getId(),
          fine.getLoanId(),
          fine.getType(),
          fine.getDescription(),
          fine.getStatus(),
          fine.isAccruing(),
          fine.charged().amount(),
          fine.balance().amount(),
          fine.getCreatedAt(),
          fine.getSettledAt(),
          fine.getTransactions().stream().map(FineLineSummary::from).toList());
    }
  }

  /**
   * @param balance owed now across open fines
   * @param blockAt borrowing stops at this balance
   * @param blocked balance has reached it
   */
  public record MemberFinesResponse(
      BigDecimal balance, BigDecimal blockAt, boolean blocked, List<FineSummary> fines) {

    static MemberFinesResponse from(MemberFines fines) {
      return new MemberFinesResponse(
          fines.balance().amount(),
          fines.blockAt().amount(),
          fines.blocked(),
          fines.fines().stream().map(FineSummary::from).toList());
    }
  }
}
