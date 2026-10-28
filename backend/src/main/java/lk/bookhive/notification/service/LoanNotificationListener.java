package lk.bookhive.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lk.bookhive.circulation.event.CheckoutReceiptRequested;
import lk.bookhive.circulation.event.LoansDueSoon;
import lk.bookhive.circulation.event.LoansOverdue;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/**
 * Loan notifications (ADR 0004): the checkout receipt (CIRC-8, step 239) and the reminders, due
 * soon (step 314) and overdue (step 315).
 */
@Component
public class LoanNotificationListener {

  private final Notifier notifier;

  public LoanNotificationListener(Notifier notifier) {
    this.notifier = notifier;
  }

  @NotificationListener
  public void onReceipt(CheckoutReceiptRequested event) {
    List<Map<String, Object>> books =
        event.lines().stream()
            .map(
                line ->
                    Map.<String, Object>of(
                        "title", line.title(),
                        "barcode", line.barcode(),
                        "dueDate", line.dueDate()))
            .toList();
    notifier.send(
        new NotificationRequest(
            event.userId(),
            NotificationType.CHECKOUT_RECEIPT,
            Map.of(
                "memberNo", event.memberNo(),
                // Shown as library time (Sri Lanka), whatever the server's own time zone
                "checkedOutAt", LocalDateTime.ofInstant(event.checkedOutAt(), LibraryTime.ZONE),
                "books", books),
            "/account/loans",
            null));
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
