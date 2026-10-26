package lk.bookhive.circulation.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.catalog.domain.CopyStatus;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.catalog.service.LendingCatalogService.LendableCopy;
import lk.bookhive.circulation.domain.Loan;
import lk.bookhive.circulation.repository.LoanRepository;
import lk.bookhive.circulation.service.BorrowingPorts.HoldQueue;
import lk.bookhive.circulation.service.FineCalculator.LateFine;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.member.service.BorrowerService.Borrower;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns at the desk (checklist steps 223-224): scan a copy, the loan closes, any late fine is
 * worked out, and staff are told where the book goes next, to its shelf or to the hold shelf for
 * the next member in the queue.
 */
@Service
public class ReturnService {

  private final LendingCatalogService catalog;
  private final LoanRepository loans;
  private final BorrowerService borrowers;
  private final SettingsService settings;
  private final HoldQueue holdQueue;
  private final AuditService audit;
  private final LibraryTime time;

  public ReturnService(
      LendingCatalogService catalog,
      LoanRepository loans,
      BorrowerService borrowers,
      SettingsService settings,
      ObjectProvider<HoldQueue> holdQueue,
      AuditService audit,
      LibraryTime time) {
    this.catalog = catalog;
    this.loans = loans;
    this.borrowers = borrowers;
    this.settings = settings;
    // Until the hold module exists, nobody is waiting: every book goes back to its shelf
    this.holdQueue = holdQueue.getIfAvailable(() -> (bookId, copyId) -> Optional.empty());
    this.audit = audit;
    this.time = time;
  }

  /** Where the returned book goes. */
  public enum Destination {
    SHELF,
    HOLD_SHELF
  }

  /**
   * @param heldFor the member it's set aside for (HOLD_SHELF only)
   */
  public record Routing(Destination destination, String shelfLocation, Borrower heldFor) {}

  /**
   * @param borrower who had it (null if the loan was already anonymised, which can't happen while
   *     it's open, but keeps the type honest)
   */
  public record ReturnResult(
      Loan loan, LendableCopy copy, Borrower borrower, LateFine fine, Routing routing) {}

  @Transactional
  public ReturnResult returnCopy(String barcode, UUID staffId) {
    UUID copyId =
        catalog
            .idByBarcode(barcode)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "COPY_NOT_FOUND", "No copy has the barcode " + barcode + "."));
    // Same lock as checkout: a return and a checkout of one copy can't interleave
    LendableCopy copy = catalog.lockForLending(copyId);
    Loan loan =
        loans
            .findFirstByCopyIdAndStatusIn(copyId, LoanRepository.OPEN)
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        "COPY_NOT_ON_LOAN",
                        copy.status() == CopyStatus.AVAILABLE
                            ? "This copy isn't on loan: it can go back on the shelf."
                            : "This copy has no open loan."));

    LocalDate today = time.today();
    Instant now = time.now();
    LateFine fine = FineCalculator.lateFine(loan.getDueDate(), today, settings.fines());
    loan.returnBack(staffId, now);

    Optional<UUID> nextMember = holdQueue.nextMemberFor(copy.bookId(), copyId);
    catalog.checkIn(copyId, nextMember.isPresent(), staffId, now);
    Routing routing =
        new Routing(
            nextMember.isPresent() ? Destination.HOLD_SHELF : Destination.SHELF,
            copy.shelfLocation(),
            nextMember.flatMap(borrowers::byId).orElse(null));

    Map<String, Object> details = new HashMap<>();
    details.put("barcode", copy.barcode());
    details.put("daysLate", fine.daysLate());
    details.put("fine", fine.amount().amount().toPlainString());
    details.put("destination", routing.destination().name());
    audit.record("loan.returned", staffId, "loan", loan.getId(), details);

    Borrower borrower =
        loan.getMemberId() == null ? null : borrowers.byId(loan.getMemberId()).orElse(null);
    return new ReturnResult(loan, copy, borrower, fine, routing);
  }
}
