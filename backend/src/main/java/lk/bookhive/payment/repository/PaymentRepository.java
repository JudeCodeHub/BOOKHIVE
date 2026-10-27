package lk.bookhive.payment.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.payment.domain.Payment;
import lk.bookhive.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByOrderId(String orderId);

  /**
   * The payment, locked until the transaction ends: two notifications for the same order (PayHere
   * retries) are applied one after the other, never both at once (step 282).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId")
  Optional<Payment> findByOrderIdForUpdate(@Param("orderId") String orderId);

  /** "My payments", newest first (step 294). */
  Page<Payment> findByMemberIdOrderByCreatedAtDesc(UUID memberId, Pageable pageable);

  /** Reconciliation job: payments still PENDING since before this time (step 286). */
  List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant before);
}
