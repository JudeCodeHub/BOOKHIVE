package lk.bookhive.circulation.service;

import java.util.List;
import lk.bookhive.circulation.domain.Loan;
import lk.bookhive.circulation.event.LoanBecameOverdue;
import lk.bookhive.circulation.repository.LoanRepository;
import lk.bookhive.common.time.LibraryTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks loans past their due date as OVERDUE (checklist step 233). Safe to run again: a loan
 * already marked is not picked up a second time.
 */
@Service
public class OverdueService {

  private final LoanRepository loans;
  private final ApplicationEventPublisher events;
  private final LibraryTime time;

  public OverdueService(LoanRepository loans, ApplicationEventPublisher events, LibraryTime time) {
    this.loans = loans;
    this.events = events;
    this.time = time;
  }

  /**
   * @return how many loans became overdue
   */
  @Transactional
  public int markOverdue() {
    List<Loan> due = loans.findBecomingOverdue(time.today());
    for (Loan loan : due) {
      loan.markOverdue();
      events.publishEvent(
          new LoanBecameOverdue(
              loan.getId(), loan.getMemberId(), loan.getCopyId(), loan.getDueDate()));
    }
    return due.size();
  }
}
