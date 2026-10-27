package lk.bookhive.payment.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.domain.Money;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One online payment of a member's fines (table {@code payments}, step 279): the order sent to
 * PayHere and what its notifications said. The amount and items are fixed when it's created; only
 * the gateway's answer changes it.
 */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

  public static final String CURRENCY = "LKR";

  @Column(nullable = false, length = 50, updatable = false)
  private String orderId;

  @Column(nullable = false, updatable = false)
  private UUID memberId;

  @Column(nullable = false, precision = 10, scale = 2, updatable = false)
  private BigDecimal amount;

  // ISO code, always exactly 3 letters (CHAR(3) in V26)
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3, updatable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  @Column(nullable = false, length = 20, updatable = false)
  private String gateway;

  @Column(length = 50)
  private String gatewayPaymentId;

  private Short gatewayStatus;

  @Column(length = 30)
  private String method;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, String> rawPayload;

  private Instant paidAt;

  @OneToMany(mappedBy = "payment", cascade = CascadeType.PERSIST)
  private List<PaymentItem> items = new ArrayList<>();

  protected Payment() {
    // for JPA
  }

  /** A new PayHere order for these fines (fine id -> amount); the total is their sum. */
  public static Payment create(String orderId, UUID memberId, Map<UUID, Money> fines) {
    if (fines.isEmpty()) {
      throw new IllegalArgumentException("A payment needs at least one fine");
    }
    Payment payment = new Payment();
    payment.orderId = orderId;
    payment.memberId = memberId;
    payment.currency = CURRENCY;
    payment.gateway = "PAYHERE";
    payment.status = PaymentStatus.PENDING;
    Money total = Money.ZERO;
    for (Map.Entry<UUID, Money> fine : fines.entrySet()) {
      payment.items.add(new PaymentItem(payment, fine.getKey(), fine.getValue()));
      total = total.plus(fine.getValue());
    }
    payment.amount = total.amount();
    return payment;
  }

  /**
   * Records what a gateway notification said (steps 281-283). Moves on from PENDING only; a later
   * notification for an already-final payment just keeps the payload (idempotent).
   *
   * @return true when the status changed (the caller then pays or leaves the fines)
   */
  public boolean applyNotification(
      PaymentStatus newStatus,
      int gatewayStatusCode,
      String gatewayPaymentId,
      String method,
      Map<String, String> payload,
      Instant now) {
    this.rawPayload = payload;
    boolean changes =
        status == PaymentStatus.PENDING && newStatus != PaymentStatus.PENDING
            // A chargeback can follow a success
            || status == PaymentStatus.SUCCEEDED && newStatus == PaymentStatus.CHARGEDBACK;
    if (!changes) {
      return false;
    }
    this.status = newStatus;
    this.gatewayStatus = (short) gatewayStatusCode;
    this.gatewayPaymentId = gatewayPaymentId;
    this.method = method;
    if (newStatus == PaymentStatus.SUCCEEDED) {
      this.paidAt = now;
    }
    return true;
  }

  public String getOrderId() {
    return orderId;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public Money getAmount() {
    return new Money(amount);
  }

  public String getCurrency() {
    return currency;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public String getGateway() {
    return gateway;
  }

  public String getGatewayPaymentId() {
    return gatewayPaymentId;
  }

  public Short getGatewayStatus() {
    return gatewayStatus;
  }

  public String getMethod() {
    return method;
  }

  public Map<String, String> getRawPayload() {
    return rawPayload;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public List<PaymentItem> getItems() {
    return Collections.unmodifiableList(items);
  }
}
