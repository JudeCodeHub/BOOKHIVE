package lk.bookhive.notification.service;

import java.util.Map;
import java.util.UUID;
import lk.bookhive.hold.event.HoldExpired;
import lk.bookhive.hold.event.HoldExpiring;
import lk.bookhive.hold.event.HoldReady;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/**
 * Hold notices (NOTIF-4, checklist step 316): the reserved book is ready, the last day to collect
 * it is coming, and it wasn't collected in time. One of each per hold (deduplicated by hold id).
 */
@Component
public class HoldNotificationListener {

  private final Notifier notifier;
  private final BorrowerService borrowers;

  public HoldNotificationListener(Notifier notifier, BorrowerService borrowers) {
    this.notifier = notifier;
    this.borrowers = borrowers;
  }

  @NotificationListener
  public void onReady(HoldReady event) {
    send(
        event.memberId(),
        NotificationType.HOLD_READY,
        Map.of("title", event.title(), "pickUpBy", event.pickUpBy()),
        "HOLD_READY:" + event.holdId());
  }

  @NotificationListener
  public void onExpiring(HoldExpiring event) {
    send(
        event.memberId(),
        NotificationType.HOLD_EXPIRING,
        Map.of("title", event.title(), "pickUpBy", event.pickUpBy()),
        "HOLD_EXPIRING:" + event.holdId());
  }

  @NotificationListener
  public void onExpired(HoldExpired event) {
    send(
        event.memberId(),
        NotificationType.HOLD_EXPIRED,
        Map.of("title", event.title()),
        "HOLD_EXPIRED:" + event.holdId());
  }

  private void send(
      UUID memberId, NotificationType type, Map<String, Object> params, String dedupeKey) {
    // Holds belong to members; notifications go to their user account
    borrowers
        .byId(memberId)
        .ifPresent(
            borrower ->
                notifier.send(
                    new NotificationRequest(
                        borrower.userId(), type, params, "/account/holds", dedupeKey)));
  }
}
