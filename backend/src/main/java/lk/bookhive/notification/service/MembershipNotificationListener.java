package lk.bookhive.notification.service;

import java.util.Map;
import lk.bookhive.member.event.MembershipApproved;
import lk.bookhive.member.event.MembershipExpiring;
import lk.bookhive.member.event.MembershipRejected;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/**
 * Membership notices (ADR 0004): the outcome of an application (MEM-3), after the decision is
 * saved, and the reminder before a membership ends (step 317), in the member's language.
 */
@Component
public class MembershipNotificationListener {

  private final Notifier notifier;

  public MembershipNotificationListener(Notifier notifier) {
    this.notifier = notifier;
  }

  @NotificationListener
  public void onApproved(MembershipApproved event) {
    notifier.send(
        new NotificationRequest(
            event.userId(),
            NotificationType.MEMBERSHIP_APPROVED,
            Map.of(
                "membershipType", event.membershipType(),
                "memberNo", event.memberNo(),
                "expiresAt", event.expiresAt()),
            "/account/card",
            null));
  }

  @NotificationListener
  public void onRejected(MembershipRejected event) {
    notifier.send(
        new NotificationRequest(
            event.userId(),
            NotificationType.MEMBERSHIP_REJECTED,
            Map.of("reason", event.reason()),
            "/account/membership",
            null));
  }

  /** Once per member and expiry date (the job may run twice). */
  @NotificationListener
  public void onExpiring(MembershipExpiring event) {
    notifier.send(
        new NotificationRequest(
            event.userId(),
            NotificationType.MEMBERSHIP_EXPIRING,
            Map.of("expiresAt", event.expiresAt()),
            "/account/membership",
            "MEMBERSHIP_EXPIRING:" + event.memberId() + ":" + event.expiresAt()));
  }
}
