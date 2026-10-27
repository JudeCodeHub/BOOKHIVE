package lk.bookhive.fine.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.catalog.service.LendingCatalogService.LendableCopy;
import lk.bookhive.circulation.service.LoanLookupService;
import lk.bookhive.circulation.service.LoanLookupService.OverdueLoan;
import lk.bookhive.common.time.LibraryTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Daily overdue accrual (FIN-1, step 271): every open loan past its due date gets its overdue fine
 * brought up to today. Each loan in its own transaction, so one problem doesn't stop the rest;
 * running it twice in a day adds nothing.
 */
@Service
public class FineAccrualService {

  private static final Logger log = LoggerFactory.getLogger(FineAccrualService.class);

  private final LoanLookupService loans;
  private final LendingCatalogService catalog;
  private final FineChargeService charges;
  private final TransactionTemplate tx;
  private final LibraryTime time;

  public FineAccrualService(
      LoanLookupService loans,
      LendingCatalogService catalog,
      FineChargeService charges,
      TransactionTemplate tx,
      LibraryTime time) {
    this.loans = loans;
    this.catalog = catalog;
    this.charges = charges;
    this.tx = tx;
    this.time = time;
  }

  /**
   * @return how many overdue loans were looked at
   */
  public int accrue() {
    LocalDate today = time.today();
    List<OverdueLoan> overdue = loans.overdueOn(today);
    Map<UUID, LendableCopy> copies =
        catalog.byIds(overdue.stream().map(OverdueLoan::copyId).toList());
    for (OverdueLoan loan : overdue) {
      LendableCopy copy = copies.get(loan.copyId());
      String title = copy == null ? "a book" : copy.title();
      try {
        tx.executeWithoutResult(
            status ->
                charges.accrueOverdue(
                    loan.loanId(), loan.memberId(), loan.dueDate(), title, today));
      } catch (RuntimeException e) {
        log.warn("Overdue fine for loan {} not updated; will retry next run", loan.loanId(), e);
      }
    }
    return overdue.size();
  }
}
