package lk.bookhive.notification.service;

/**
 * The one way to notify someone (ADR 0004). Listeners turn their event into a {@link
 * NotificationRequest}; the notifier looks up the recipient, applies their preferences, stores the
 * in-app message and queues email/SMS in the outbox for the dispatcher to send with retries.
 */
public interface Notifier {

  void send(NotificationRequest request);
}
