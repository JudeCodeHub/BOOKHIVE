package lk.bookhive.notification.service;

import java.util.Map;
import java.util.UUID;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/**
 * Notifying a member when an event only knows the member id (holds, fines): notifications go to the
 * member's user account. A member that no longer exists is skipped.
 */
@Component
public class MemberNotifier {

  private final Notifier notifier;
  private final BorrowerService borrowers;

  public MemberNotifier(Notifier notifier, BorrowerService borrowers) {
    this.notifier = notifier;
    this.borrowers = borrowers;
  }

  public void send(
      UUID memberId,
      NotificationType type,
      Map<String, Object> params,
      String link,
      String dedupeKey) {
    borrowers
        .byId(memberId)
        .ifPresent(
            borrower ->
                notifier.send(
                    new NotificationRequest(borrower.userId(), type, params, link, dedupeKey)));
  }
}
