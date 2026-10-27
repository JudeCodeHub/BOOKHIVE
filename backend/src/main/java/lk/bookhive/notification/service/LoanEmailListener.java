package lk.bookhive.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lk.bookhive.circulation.event.CheckoutReceiptRequested;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/** Loan emails: the checkout receipt (CIRC-8, step 239), after the checkout is saved (ADR 0004). */
@Component
public class LoanEmailListener {

  private final Notifier notifier;

  public LoanEmailListener(Notifier notifier) {
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
}
