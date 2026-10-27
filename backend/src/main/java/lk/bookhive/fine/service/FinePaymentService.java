package lk.bookhive.fine.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.domain.Money;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.fine.domain.Fine;
import lk.bookhive.fine.domain.FineStatus;
import lk.bookhive.fine.domain.PaymentMethod;
import lk.bookhive.fine.repository.FineRepository;
import lk.bookhive.fine.repository.FineTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash at the desk (FIN-6, checklist step 276). Staff take the money, write a numbered receipt from
 * the receipt book, and record it here. The amount is spread over the member's open fines, oldest
 * first (or only the fines staff picked), as PAYMENT lines carrying the receipt number.
 */
@Service
public class FinePaymentService {

  /** What one fine got from the payment. */
  public record Allocation(Fine fine, Money amount) {}

  /**
   * @param balanceAfter what the member still owes afterwards
   */
  public record CashReceipt(
      String receiptNo, Money total, List<Allocation> allocations, Money balanceAfter) {}

  private final FineRepository fines;
  private final FineTransactionRepository transactions;
  private final FineQueryService query;
  private final AuditService audit;
  private final LibraryTime time;

  public FinePaymentService(
      FineRepository fines,
      FineTransactionRepository transactions,
      FineQueryService query,
      AuditService audit,
      LibraryTime time) {
    this.fines = fines;
    this.transactions = transactions;
    this.query = query;
    this.audit = audit;
    this.time = time;
  }

  /**
   * Records a cash payment.
   *
   * @param fineIds the fines to pay, in that order; empty = all open fines, oldest first
   * @throws BusinessRuleException AMOUNT_MORE_THAN_OWED when it's more than those fines' balance
   * @throws ConflictException RECEIPT_ALREADY_USED when the receipt number was used before
   */
  @Transactional
  public CashReceipt recordCash(
      UUID memberId, Money amount, String receiptNo, List<UUID> fineIds, UUID staffId) {
    String receipt = receiptNo.strip().toUpperCase();
    if (amount.isNegative() || amount.isZero()) {
      throw new FieldValidationException(
          "amount", "AMOUNT_NOT_POSITIVE", "Enter an amount above 0.");
    }
    if (transactions.existsByReference(receipt)) {
      throw new ConflictException(
          "RECEIPT_ALREADY_USED", "Receipt " + receipt + " has already been recorded.");
    }

    List<Fine> payable = payableFines(memberId, fineIds);
    Money owed = payable.stream().map(Fine::balance).reduce(Money.ZERO, Money::plus);
    if (amount.compareTo(owed) > 0) {
      throw new BusinessRuleException(
          "AMOUNT_MORE_THAN_OWED",
          "That's more than is owed (" + owed.amount().toPlainString() + "). Give change.");
    }

    // Oldest first, each fine up to its balance, until the money runs out
    Instant now = time.now();
    Money left = amount;
    List<Allocation> allocations = new ArrayList<>();
    for (Fine fine : payable) {
      if (left.isZero()) {
        break;
      }
      Money part = left.min(fine.balance());
      if (part.isZero() || part.isNegative()) {
        continue;
      }
      fine.pay(part, PaymentMethod.CASH, receipt, staffId, now);
      allocations.add(new Allocation(fine, part));
      left = left.minus(part);
    }
    fines.flush();

    Map<String, Object> details = new HashMap<>();
    details.put("receiptNo", receipt);
    details.put("amount", amount.amount().toPlainString());
    details.put("fines", allocations.stream().map(a -> a.fine().getId().toString()).toList());
    audit.record("fine.cash_payment", staffId, "member", memberId, details);
    return new CashReceipt(receipt, amount, allocations, query.of(memberId));
  }

  private List<Fine> payableFines(UUID memberId, List<UUID> fineIds) {
    List<Fine> open =
        fines.findByMemberIdAndStatusInOrderByCreatedAt(memberId, List.of(FineStatus.OPEN));
    if (fineIds == null || fineIds.isEmpty()) {
      return open;
    }
    Map<UUID, Fine> byId = new HashMap<>();
    open.forEach(fine -> byId.put(fine.getId(), fine));
    List<Fine> picked = new ArrayList<>();
    for (UUID id : fineIds) {
      Fine fine = byId.get(id);
      if (fine == null) {
        throw new BusinessRuleException(
            "FINE_NOT_PAYABLE", "One of the chosen fines isn't open for this member.");
      }
      picked.add(fine);
    }
    return picked;
  }
}
