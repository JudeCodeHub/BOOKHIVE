package lk.bookhive.notification.service;

import java.util.List;
import java.util.Map;
import lk.bookhive.fine.event.FinePaymentRecorded;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/**
 * "Payment received" (NOTIF-4, checklist step 318): a receipt for money paid towards fines, by
 * email and in-app, once per payment (its receipt number or order id). Can't be switched off: it's
 * the member's proof of payment.
 */
@Component
public class PaymentNotificationListener {

  private final MemberNotifier members;

  public PaymentNotificationListener(MemberNotifier members) {
    this.members = members;
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
    members.send(
        event.memberId(),
        NotificationType.PAYMENT_RECEIVED,
        Map.of(
            "amount", event.amount().amount(),
            "reference", event.reference(),
            "items", items),
        "/account/fines",
        "PAYMENT_RECEIVED:" + event.reference());
  }
}
