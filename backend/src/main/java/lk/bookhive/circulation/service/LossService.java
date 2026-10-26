package lk.bookhive.circulation.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.admin.settings.Settings.Fines;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.catalog.service.LendingCatalogService.LendableCopy;
import lk.bookhive.circulation.domain.Loan;
import lk.bookhive.circulation.domain.LoanStatus;
import lk.bookhive.circulation.event.LoanDamaged;
import lk.bookhive.circulation.event.LoanLost;
import lk.bookhive.circulation.repository.LoanRepository;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.domain.Money;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lost and damaged items (PRD §4.4, CIRC-6, checklist step 229).
 *
 * <ul>
 *   <li>Lost: replacement cost (the copy's price, or the default from settings) plus a processing
 *       fee. The loan and the copy become LOST; the loan stays the member's until it's found or
 *       paid for.
 *   <li>Damaged: an amount staff choose, at most the replacement cost. The copy's condition becomes
 *       DAMAGED and, if it's back, it goes for repair.
 * </ul>
 *
 * The charges go out as events; the fine module records them on the member's account.
 */
@Service
public class LossService {

  /** What was charged. */
  public record Charge(Loan loan, LendableCopy copy, Money amount, Money processingFee) {

    public Money total() {
      return amount.plus(processingFee);
    }
  }

  private final LoanRepository loans;
  private final LendingCatalogService catalog;
  private final SettingsService settings;
  private final ApplicationEventPublisher events;
  private final AuditService audit;
  private final LibraryTime time;

  public LossService(
      LoanRepository loans,
      LendingCatalogService catalog,
      SettingsService settings,
      ApplicationEventPublisher events,
      AuditService audit,
      LibraryTime time) {
    this.loans = loans;
    this.catalog = catalog;
    this.settings = settings;
    this.events = events;
    this.audit = audit;
    this.time = time;
  }

  /** The member says the book is lost: charge replacement + processing fee. */
  @Transactional
  public Charge markLost(UUID loanId, UUID staffId) {
    Loan loan = require(loanId);
    LendableCopy copy = catalog.lockForLending(loan.getCopyId());
    if (!loan.getStatus().isOpen()) {
      throw new BusinessRuleException(
          "LOAN_NOT_OPEN", "Only a loan that's still out can be marked lost.");
    }
    Instant now = time.now();
    Fines fines = settings.fines();
    Money cost = replacementCost(copy, fines);
    Money fee = new Money(fines.lostProcessingFee());

    loan.markLost();
    catalog.markLost(copy.copyId(), staffId, now);
    events.publishEvent(
        new LoanLost(
            loan.getId(),
            loan.getMemberId(),
            copy.copyId(),
            copy.bookId(),
            copy.title(),
            cost,
            fee,
            now,
            staffId));
    audit.record(
        "loan.lost",
        staffId,
        "loan",
        loan.getId(),
        Map.of("barcode", copy.barcode(), "replacementCost", cost.amount(), "fee", fee.amount()));
    return new Charge(loan, copy, cost, fee);
  }

  /**
   * The copy was damaged on this loan: charge what staff decide, up to the replacement cost.
   *
   * @param note what's wrong, e.g. "water damage, pages 40-80 stuck"
   */
  @Transactional
  public Charge markDamaged(UUID loanId, Money amount, String note, UUID staffId) {
    Loan loan = require(loanId);
    LendableCopy copy = catalog.lockForLending(loan.getCopyId());
    if (loan.getStatus() == LoanStatus.LOST) {
      throw new BusinessRuleException(
          "LOAN_LOST", "This copy is marked lost; it can't also be charged as damaged.");
    }
    if (loan.getMemberId() == null) {
      throw new BusinessRuleException(
          "LOAN_ANONYMISED", "This loan no longer records who borrowed it.");
    }
    Money cap = replacementCost(copy, settings.fines());
    if (amount.isNegative() || amount.isZero() || amount.compareTo(cap) > 0) {
      throw new FieldValidationException(
          "amount",
          "DAMAGE_AMOUNT_OUT_OF_RANGE",
          "Charge more than 0 and at most the replacement cost, LKR " + cap.amount() + ".");
    }
    Instant now = time.now();
    String cleanNote = note.strip();

    catalog.markDamaged(copy.copyId(), cleanNote, staffId, now);
    events.publishEvent(
        new LoanDamaged(
            loan.getId(),
            loan.getMemberId(),
            copy.copyId(),
            copy.bookId(),
            copy.title(),
            amount,
            cleanNote,
            now,
            staffId));
    audit.record(
        "loan.damaged",
        staffId,
        "loan",
        loan.getId(),
        Map.of("barcode", copy.barcode(), "amount", amount.amount(), "note", cleanNote));
    return new Charge(loan, copy, amount, Money.ZERO);
  }

  /** What the library paid for this copy, or the default when it isn't recorded. */
  private static Money replacementCost(LendableCopy copy, Fines fines) {
    return copy.price() != null && !copy.price().isZero()
        ? copy.price()
        : new Money(fines.lostDefaultPrice());
  }

  private Loan require(UUID loanId) {
    return loans
        .findById(loanId)
        .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Loan not found."));
  }
}
