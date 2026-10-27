package lk.bookhive.fine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.Money;
import org.hibernate.annotations.Immutable;

/**
 * One line of a fine's ledger (table {@code fine_transactions}, FIN-3). Written only by {@link
 * Fine}; never edited (the database refuses updates and deletes too).
 */
@Entity
@Table(name = "fine_transactions")
@Immutable
public class FineTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fine_id", nullable = false, updatable = false)
  private Fine fine;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private FineTransactionKind kind;

  @Column(nullable = false, precision = 10, scale = 2, updatable = false)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, updatable = false)
  private PaymentMethod method;

  @Column(length = 100, updatable = false)
  private String reference;

  @Column(length = 500, updatable = false)
  private String reason;

  @Column(updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected FineTransaction() {
    // for JPA
  }

  FineTransaction(
      Fine fine,
      FineTransactionKind kind,
      Money amount,
      PaymentMethod method,
      String reference,
      String reason,
      UUID actorId,
      Instant createdAt) {
    this.fine = fine;
    this.kind = kind;
    this.amount = amount.amount();
    this.method = method;
    this.reference = reference;
    this.reason = reason;
    this.actorId = actorId;
    this.createdAt = createdAt;
  }

  /** The amount with its direction: + for charges and refunds, - for the rest. */
  public Money signedAmount() {
    return new Money(kind.sign() > 0 ? amount : amount.negate());
  }

  public UUID getId() {
    return id;
  }

  public FineTransactionKind getKind() {
    return kind;
  }

  public Money getAmount() {
    return new Money(amount);
  }

  public PaymentMethod getMethod() {
    return method;
  }

  public String getReference() {
    return reference;
  }

  public String getReason() {
    return reason;
  }

  public UUID getActorId() {
    return actorId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
