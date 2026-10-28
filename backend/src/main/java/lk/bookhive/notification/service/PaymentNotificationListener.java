package lk.bookhive.notification.service;

import java.util.List;
import java.util.Map;
import lk.bookhive.fine.event.FinePaymentRecorded;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/**
 * "Payment received" (NOTIF-4, checklist step 318): a receipt for money paid towards fines, by
 * email and in-app, once per payment (its receipt number or order id). Can't be switched off: it's
 * the member's proof of payment.
 */
@Component
public class PaymentNotificationListener {

  private final Notifier notifier;
  private final BorrowerService borrowers;

  public PaymentNotificationListener(Notifier notifier, BorrowerService borrowers) {
    this.notifier = notifier;
    this.borrowers = borrowers;
  }

  @NotificationListener
  public void onPayment(FinePaymentRecorded event) {
    List<Map<String, Object>> items =
        event.items().stream()
            .map(
                item ->
                    Map.<String, Object>of(
                        "description", item.description(), "amount", item.amount().amount()))
            .toList();
    borrowers
        .byId(event.memberId())
        .ifPresent(
            borrower ->
                notifier.send(
                    new NotificationRequest(
                        borrower.userId(),
                        NotificationType.PAYMENT_RECEIVED,
                        Map.of(
                            "amount", event.amount().amount(),
                            "reference", event.reference(),
                            "items", items),
                        "/account/fines",
                        "PAYMENT_RECEIVED:" + event.reference())));
  }
}
