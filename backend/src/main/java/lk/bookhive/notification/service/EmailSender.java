package lk.bookhive.notification.service;

/**
 * Sends one email. An interface so the delivery method can change (SMTP today, an outbox with
 * retries in checklist step 355) and tests can record emails instead of sending them.
 */
public interface EmailSender {

  void send(EmailMessage message);

  /** A plain-text email. */
  record EmailMessage(String to, String subject, String text) {}
}
