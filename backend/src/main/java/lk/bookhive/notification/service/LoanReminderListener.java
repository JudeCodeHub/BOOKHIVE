package lk.bookhive.notification.service;

import java.util.List;
import java.util.Map;
import lk.bookhive.circulation.event.LoansDueSoon;
import lk.bookhive.circulation.event.LoansOverdue;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/** Loan reminders (NOTIF-4): due soon (step 314), overdue (step 315). */
@Component
public class LoanReminderListener {

  private final Notifier notifier;

  public LoanReminderListener(Notifier notifier) {
    this.notifier = notifier;
  }

  @NotificationListener
  public void onDueSoon(LoansDueSoon event) {
    List<Map<String, Object>> books =
        event.books().stream()
            .map(book -> Map.<String, Object>of("title", book.title(), "dueDate", event.dueDate()))
            .toList();
    notifier.send(
        new NotificationRequest(
            event.userId(),
            NotificationType.DUE_SOON,
            Map.of("books", books),
            "/account/loans",
            // One reminder per member and due date, even if the job runs twice
            "DUE_SOON:" + event.memberId() + ":" + event.dueDate()));
  }

  /** Day 1 and day 7 overdue notices (step 315); one per member and notice day. */
  @NotificationListener
  public void onOverdue(LoansOverdue event) {
    List<Map<String, Object>> books =
        event.books().stream()
            .map(
                book ->
                    Map.<String, Object>of(
                        "title", book.title(),
                        "dueDate", book.dueDate(),
                        "daysLate", event.daysLate()))
            .toList();
    notifier.send(
        new NotificationRequest(
            event.userId(),
            NotificationType.OVERDUE,
            Map.of("books", books),
            "/account/loans",
            "OVERDUE:" + event.memberId() + ":" + event.noticeDate() + ":" + event.daysLate()));
  }
}
