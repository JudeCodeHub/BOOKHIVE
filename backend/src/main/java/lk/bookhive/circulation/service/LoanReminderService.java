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
   * Books due in N days (settings, 2 by default): one {@link LoansDueSoon} per member.
   *
   * @return how many members were reminded
   */
  @Transactional(readOnly = true)
  public int remindDueSoon() {
    LocalDate dueDate = time.today().plusDays(settings.notifications().dueSoonDaysBefore());
    List<Loan> due = loans.findByStatusInAndDueDate(LoanRepository.OPEN, dueDate);
    Map<UUID, LendableCopy> copies = catalog.byIds(due.stream().map(Loan::getCopyId).toList());
    Map<UUID, List<Loan>> byMember =
        due.stream()
            .collect(
                Collectors.groupingBy(Loan::getMemberId, LinkedHashMap::new, Collectors.toList()));
    int reminded = 0;
    for (Map.Entry<UUID, List<Loan>> member : byMember.entrySet()) {
      var borrower = borrowers.byId(member.getKey());
      if (borrower.isEmpty()) {
        continue;
      }
      List<LoansDueSoon.Book> books =
          member.getValue().stream()
              .map(
                  loan ->
                      new LoansDueSoon.Book(
                          loan.getId(),
                          copies.containsKey(loan.getCopyId())
                              ? copies.get(loan.getCopyId()).title()
                              : ""))
              .toList();
      events.publishEvent(
          new LoansDueSoon(member.getKey(), borrower.get().userId(), dueDate, books));
      reminded++;
    }
    return reminded;
  }
}
