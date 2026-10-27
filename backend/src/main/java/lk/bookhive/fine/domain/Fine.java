package lk.bookhive.fine.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.domain.Money;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.ConflictException;

/**
 * One reason a member owes money (table {@code fines}, FIN-3), with its ledger. The balance is
 * never stored or edited: it's the sum of the ledger lines, and every change adds a line.
 *
 * <p>The member, loan and actors are referenced by id: they belong to other modules. The rules
 * (rates, caps, who may waive how much) are in the fine services.
 */
@Entity
@Table(name = "fines")
public class Fine extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID memberId;

  @Column(updatable = false)
  private UUID loanId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private FineType type;

  @Column(nullable = false, length = 300)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private FineStatus status;

  @Column(nullable = false)
  private boolean accruing;

  private Instant settledAt;

  // Saved together with the fine (cascade), only ever added to
  @OneToMany(mappedBy = "fine", cascade = CascadeType.PERSIST)
  @OrderBy("createdAt")
  private List<FineTransaction> transactions = new ArrayList<>();

  protected Fine() {
    // for JPA
  }

  /**
   * A new fine with its first charge.
   *
   * @param accruing an overdue fine that keeps growing until the book comes back
   */
  public static Fine open(
      UUID memberId,
      UUID loanId,
      FineType type,
      String description,
      boolean accruing,
      Money firstCharge,
      String reason,
      UUID actorId,
      Instant now) {
    Fine fine = new Fine();
    fine.memberId = memberId;
    fine.loanId = loanId;
    fine.type = type;
    fine.description = description;
    fine.status = FineStatus.OPEN;
    fine.accruing = accruing;
    fine.charge(firstCharge, reason, actorId, now);
    return fine;
  }

  /** Adds to what's owed (e.g. another day late, the processing fee on a lost book). */
  public void charge(Money amount, String reason, UUID actorId, Instant now) {
    requireNotCancelled();
    add(FineTransactionKind.CHARGE, amount, null, null, reason, actorId, now);
  }

  /** Money received. Can't be more than what's owed. */
  public void pay(Money amount, PaymentMethod method, String reference, UUID actorId, Instant now) {
    requireNotCancelled();
    requireAtMostBalance(amount);
    add(FineTransactionKind.PAYMENT, amount, method, reference, null, actorId, now);
  }

  /** Let off (part of) what's owed, with a reason. Role limits are checked by the service. */
  public void waive(Money amount, String reason, UUID actorId, Instant now) {
    requireNotCancelled();
    requireAtMostBalance(amount);
    add(FineTransactionKind.WAIVER, amount, null, null, reason, actorId, now);
  }

  /**
   * Withdraws the fine: everything charged and not waived is reversed (e.g. a lost book turned up).
   * If some was paid, the balance goes negative: the library owes the member a {@link #refund}.
   */
  public void cancel(String reason, UUID actorId, Instant now) {
    requireNotCancelled();
    Money toReverse = charged().minus(sum(FineTransactionKind.WAIVER));
    if (toReverse.amount().signum() > 0) {
      add(FineTransactionKind.CANCELLATION, toReverse, null, null, reason, actorId, now);
    }
    this.accruing = false;
    this.status = FineStatus.CANCELLED;
    this.settledAt = now;
  }

  /** Money given back after a cancellation. Can't be more than the library owes. */
  public void refund(
      Money amount,
      PaymentMethod method,
      String reference,
      String reason,
      UUID actorId,
      Instant now) {
    if (amount.compareTo(Money.ZERO.minus(balance())) > 0) {
      throw new BusinessRuleException(
          "REFUND_TOO_LARGE", "The refund is more than the member is owed.");
    }
    add(FineTransactionKind.REFUND, amount, method, reference, reason, actorId, now);
  }

  /** The book came back: an overdue fine stops growing (step 272). */
  public void stopAccruing(Instant now) {
    this.accruing = false;
    updateStatus(now);
  }

  /** What's still owed (negative: the library owes the member). */
  public Money balance() {
    return transactions.stream().map(FineTransaction::signedAmount).reduce(Money.ZERO, Money::plus);
  }

  /** Everything charged so far. */
  public Money charged() {
    return sum(FineTransactionKind.CHARGE);
  }

  private Money sum(FineTransactionKind kind) {
    return transactions.stream()
        .filter(tx -> tx.getKind() == kind)
        .map(FineTransaction::getAmount)
        .reduce(Money.ZERO, Money::plus);
  }

  private void add(
      FineTransactionKind kind,
      Money amount,
      PaymentMethod method,
      String reference,
      String reason,
      UUID actorId,
      Instant now) {
    if (amount.isNegative() || amount.isZero()) {
      throw new IllegalArgumentException("Ledger amounts are positive: " + amount);
    }
    transactions.add(
        new FineTransaction(this, kind, amount, method, reference, reason, actorId, now));
    updateStatus(now);
  }

  private void updateStatus(Instant now) {
    if (status == FineStatus.CANCELLED) {
      return;
    }
    boolean settled = !accruing && balance().amount().signum() <= 0;
    this.status = settled ? FineStatus.SETTLED : FineStatus.OPEN;
    this.settledAt = settled ? now : null;
  }

  private void requireNotCancelled() {
    if (status == FineStatus.CANCELLED) {
      throw new ConflictException("FINE_CANCELLED", "This fine was cancelled.");
    }
  }

  private void requireAtMostBalance(Money amount) {
    if (amount.compareTo(balance()) > 0) {
      throw new BusinessRuleException(
          "AMOUNT_MORE_THAN_OWED", "That's more than is owed on this fine (" + balance() + ").");
    }
  }

  public UUID getMemberId() {
    return memberId;
  }

  public UUID getLoanId() {
    return loanId;
  }

  public FineType getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  public FineStatus getStatus() {
    return status;
  }

  public boolean isAccruing() {
    return accruing;
  }

  public Instant getSettledAt() {
    return settledAt;
  }

  public List<FineTransaction> getTransactions() {
    return Collections.unmodifiableList(transactions);
  }
}
