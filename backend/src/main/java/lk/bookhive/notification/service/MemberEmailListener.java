package lk.bookhive.notification.service;

import java.util.Map;
import lk.bookhive.member.event.MembershipApproved;
import lk.bookhive.member.event.MembershipRejected;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/**
 * Tells applicants the outcome of their membership application (MEM-3), after the decision is
 * saved, in their language (ADR 0004).
 */
@Component
public class MemberEmailListener {

  private final Notifier notifier;

  public MemberEmailListener(Notifier notifier) {
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
}
