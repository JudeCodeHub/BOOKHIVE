package lk.bookhive.notification.service;

/**
 * Sends one email (step 310). An interface so the delivery method can change and tests can record
 * emails instead of sending them. Callers normally don't use it directly: the outbox dispatcher
 * does, with retries.
 */
public interface EmailSender {

  void send(EmailMessage message);

  /**
   * An email with a plain-text body and, optionally, an HTML version (email apps show the HTML when
   * they can, the text otherwise).
   */
  record EmailMessage(String to, String subject, String text, String html) {

    /** Plain text only. */
    public EmailMessage(String to, String subject, String text) {
      this(to, subject, text, null);
    }
  }
}
