package lk.bookhive.circulation.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.catalog.service.LendingCatalogService.LendableCopy;
import lk.bookhive.circulation.domain.Loan;
import lk.bookhive.circulation.event.LoansDueSoon;
import lk.bookhive.circulation.event.LoansOverdue;
import lk.bookhive.circulation.repository.LoanRepository;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.member.service.BorrowerService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the loans members should be reminded about (NOTIF-4, steps 314-315) and announces them, one
 * event per member; the notification module decides how to tell them. Running it twice in a day
 * sends nothing new (the notifications are deduplicated by member and date).
 */
@Service
public class LoanReminderService {

  /** Overdue notices go out this many days after the due date (PRD NOTIF-4: day 1 and day 7). */
  public static final List<Integer> OVERDUE_NOTICE_DAYS = List.of(1, 7);

  private final LoanRepository loans;
  private final LendingCatalogService catalog;
  private final BorrowerService borrowers;
  private final SettingsService settings;
  private final ApplicationEventPublisher events;
  private final LibraryTime time;

  public LoanReminderService(
      LoanRepository loans,
      LendingCatalogService catalog,
      BorrowerService borrowers,
      SettingsService settings,
      ApplicationEventPublisher events,
      LibraryTime time) {
    this.loans = loans;
    this.catalog = catalog;
    this.borrowers = borrowers;
    this.settings = settings;
    this.events = events;
    this.time = time;
  }

  /**
   * Overdue notices (step 315): books whose due date was 1 or 7 days ago, one {@link LoansOverdue}
   * per member and notice day. Books returned since are no longer open, so they're left out
   * automatically.
   *
   * @return how many notices (member and day) were announced
   */
  @Transactional(readOnly = true)
  public int remindOverdue() {
    LocalDate today = time.today();
    int notices = 0;
    for (int daysLate : OVERDUE_NOTICE_DAYS) {
      LocalDate dueDate = today.minusDays(daysLate);
      List<Loan> late = loans.findByStatusInAndDueDate(LoanRepository.OPEN, dueDate);
      Map<UUID, LendableCopy> copies = catalog.byIds(late.stream().map(Loan::getCopyId).toList());
      for (Map.Entry<UUID, List<Loan>> member : byMember(late).entrySet()) {
        var borrower = borrowers.byId(member.getKey());
        if (borrower.isEmpty()) {
          continue;
        }
        List<LoansOverdue.Book> books =
            member.getValue().stream()
                .map(
                    loan ->
                        new LoansOverdue.Book(
                            loan.getId(), titleOf(copies, loan), loan.getDueDate()))
                .toList();
        events.publishEvent(
            new LoansOverdue(member.getKey(), borrower.get().userId(), daysLate, today, books));
        notices++;
      }
    }
    return notices;
  }

  /**
   * Books due in N days (settings, 2 by default): one {@link LoansDueSoon} per member.
   *
   * @return how many members were reminded
   */
  @Transactional(readOnly = true)
  public int remindDueSoon() {
    LocalDate dueDate = time.today().plusDays(settings.notifications().dueSoonDaysBefore());
    List<Loan> due = loans.findByStatusInAndDueDate(LoanRepository.OPEN, dueDate);
    Map<UUID, LendableCopy> copies = catalog.byIds(due.stream().map(Loan::getCopyId).toList());
    Map<UUID, List<Loan>> byMember = byMember(due);
    int reminded = 0;
    for (Map.Entry<UUID, List<Loan>> member : byMember.entrySet()) {
      var borrower = borrowers.byId(member.getKey());
      if (borrower.isEmpty()) {
        continue;
      }
      List<LoansDueSoon.Book> books =
          member.getValue().stream()
              .map(loan -> new LoansDueSoon.Book(loan.getId(), titleOf(copies, loan)))
              .toList();
      events.publishEvent(
          new LoansDueSoon(member.getKey(), borrower.get().userId(), dueDate, books));
      reminded++;
    }
    return reminded;
  }

  private static Map<UUID, List<Loan>> byMember(List<Loan> loans) {
    return loans.stream()
        .collect(Collectors.groupingBy(Loan::getMemberId, LinkedHashMap::new, Collectors.toList()));
  }

  private static String titleOf(Map<UUID, LendableCopy> copies, Loan loan) {
    LendableCopy copy = copies.get(loan.getCopyId());
    return copy == null ? "" : copy.title();
  }
}
