package lk.bookhive.circulation.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lk.bookhive.admin.event.ClosedDaysChanged;
import lk.bookhive.admin.service.LibraryHoursService;
import lk.bookhive.circulation.domain.Loan;
import lk.bookhive.circulation.domain.LoanStatus;
import lk.bookhive.circulation.repository.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A day became closed (new holiday, shorter weekly hours: step 357): books due on it are now due on
 * the next open day, so nobody is fined for a day the library was shut (CIRC-5). Runs in the
 * admin's transaction. Overdue loans keep their date: it has already passed.
 */
@Component
public class ClosedDayDueDateListener {

  private static final Logger log = LoggerFactory.getLogger(ClosedDayDueDateListener.class);

  private final LoanRepository loans;
  private final LibraryHoursService hours;

  public ClosedDayDueDateListener(LoanRepository loans, LibraryHoursService hours) {
    this.loans = loans;
    this.hours = hours;
  }

  @EventListener
  public void onClosedDaysChanged(ClosedDaysChanged event) {
    List<Loan> due = loans.findByStatusAndDueDateGreaterThanEqual(LoanStatus.ACTIVE, event.from());
    // Each due date is looked up once, however many loans share it
    Map<LocalDate, LocalDate> moveTo = new HashMap<>();
    int moved = 0;
    for (Loan loan : due) {
      LocalDate target = moveTo.computeIfAbsent(loan.getDueDate(), hours::nextOpenDay);
      if (!target.equals(loan.getDueDate())) {
        loan.moveDueDate(target);
        moved++;
      }
    }
    if (moved > 0) {
      log.info("Moved {} due date(s) off newly closed days", moved);
    }
  }
}
