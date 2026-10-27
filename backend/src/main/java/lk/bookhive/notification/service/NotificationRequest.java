package lk.bookhive.notification.service;

import java.util.Map;
import java.util.UUID;
import lk.bookhive.notification.domain.NotificationType;

/**
 * What a listener asks the {@link Notifier} to send (ADR 0004): who, what, the values for the
 * template, and where the in-app message links to. The notifier works out the rest (address,
 * language, channels, rendering).
 *
 * @param userId the account to notify (members are reached through their user account)
 * @param params template values, e.g. {@code title}, {@code dueDate}
 * @param link path in the website without the language, e.g. {@code /account/loans}; may be null
 * @param dedupeKey the same key is only ever queued once, e.g. {@code DUE_SOON:<loanId>:<date>};
 *     null when duplicates can't happen (one event, one message)
 */
public record NotificationRequest(
    UUID userId, NotificationType type, Map<String, Object> params, String link, String dedupeKey) {

  public NotificationRequest {
    params = params == null ? Map.of() : Map.copyOf(params);
  }
}
