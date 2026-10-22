package lk.bookhive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lk.bookhive.notification.service.EmailSender;

/**
 * Used instead of SMTP in integration tests (see IntegrationTest): keeps every "sent" email in
 * memory so tests can read them, e.g. to click the verification link.
 */
public class RecordingEmailSender implements EmailSender {

  private final List<EmailMessage> sent = new ArrayList<>();

  @Override
  public synchronized void send(EmailMessage message) {
    sent.add(message);
  }

  public synchronized List<EmailMessage> sent() {
    return List.copyOf(sent);
  }

  /** The most recent email to this address, if any. */
  public synchronized Optional<EmailMessage> lastTo(String to) {
    return sent.reversed().stream().filter(m -> m.to().equals(to)).findFirst();
  }

  public synchronized void clear() {
    sent.clear();
  }
}
