package lk.bookhive.notification.service;

import lk.bookhive.auth.event.EmailVerificationRequested;
import lk.bookhive.notification.config.MailProperties;
import lk.bookhive.notification.service.EmailSender.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Sends the emails the auth module asks for. Runs only after the database transaction has
 * committed, so we never email about an account that wasn't actually saved.
 *
 * <p>English only for now; Sinhala/Tamil templates come in checklist step 357. The link already
 * opens the website in the user's language.
 */
@Component
public class AuthEmailListener {

  private static final Logger log = LoggerFactory.getLogger(AuthEmailListener.class);

  private final EmailSender emailSender;
  private final MailProperties properties;

  public AuthEmailListener(EmailSender emailSender, MailProperties properties) {
    this.emailSender = emailSender;
    this.properties = properties;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEmailVerificationRequested(EmailVerificationRequested event) {
    String link =
        UriComponentsBuilder.fromUriString(properties.frontendUrl())
            .pathSegment(event.language().code(), "verify-email")
            .queryParam("token", event.token())
            .toUriString();

    String text =
        """
        Hello %s,

        Welcome to BookHive! Please confirm your email address by opening this link:

        %s

        If you didn't create a BookHive account, you can ignore this email.

        BookHive
        """
            .formatted(event.fullName(), link);

    try {
      emailSender.send(new EmailMessage(event.email(), "Verify your BookHive email", text));
    } catch (RuntimeException e) {
      // The account is already saved; a failed email must not turn registration into an error.
      // Reliable delivery with retries comes with the outbox (checklist step 355).
      log.error("Could not send verification email to user {}", event.userId(), e);
    }
  }
}
