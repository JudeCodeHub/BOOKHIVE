package lk.bookhive.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lk.bookhive.common.domain.Money;
import org.hibernate.annotations.Immutable;

/** One fine covered by a payment, and how much it gets (table {@code payment_items}). */
@Entity
@Table(name = "payment_items")
@Immutable
public class PaymentItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payment_id", nullable = false, updatable = false)
  private Payment payment;

  @Column(nullable = false, updatable = false)
  private UUID fineId;

  @Column(nullable = false, precision = 10, scale = 2, updatable = false)
  private BigDecimal amount;

  protected PaymentItem() {
    // for JPA
  }

  PaymentItem(Payment payment, UUID fineId, Money amount) {
    this.payment = payment;
    this.fineId = fineId;
    this.amount = amount.amount();
  }

  public UUID getId() {
    return id;
  }

  public UUID getFineId() {
    return fineId;
  }

  public Money getAmount() {
    return new Money(amount);
  }
}
