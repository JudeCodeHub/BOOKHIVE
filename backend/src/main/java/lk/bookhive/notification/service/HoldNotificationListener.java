package lk.bookhive.notification.service;

import java.util.Map;
import lk.bookhive.hold.event.HoldExpired;
import lk.bookhive.hold.event.HoldExpiring;
import lk.bookhive.hold.event.HoldReady;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/**
 * Hold notices (NOTIF-4, checklist step 316): the reserved book is ready, the last day to collect
 * it is coming, and it wasn't collected in time. One of each per hold (deduplicated by hold id).
 */
@Component
public class HoldNotificationListener {

  private final MemberNotifier members;

  public HoldNotificationListener(MemberNotifier members) {
    this.members = members;
  }

  @NotificationListener
  public void onReady(HoldReady event) {
    members.send(
        event.memberId(),
        NotificationType.HOLD_READY,
        Map.of("title", event.title(), "pickUpBy", event.pickUpBy()),
        "/account/holds",
        "HOLD_READY:" + event.holdId());
  }

  @NotificationListener
  public void onExpiring(HoldExpiring event) {
    members.send(
        event.memberId(),
        NotificationType.HOLD_EXPIRING,
        Map.of("title", event.title(), "pickUpBy", event.pickUpBy()),
        "/account/holds",
        "HOLD_EXPIRING:" + event.holdId());
  }

  @NotificationListener
  public void onExpired(HoldExpired event) {
    members.send(
        event.memberId(),
        NotificationType.HOLD_EXPIRED,
        Map.of("title", event.title()),
        "/account/holds",
        "HOLD_EXPIRED:" + event.holdId());
  }
}
