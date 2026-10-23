package lk.bookhive.notification.service;

import java.util.UUID;
import lk.bookhive.auth.event.EmailVerificationRequested;
import lk.bookhive.auth.event.PasswordChanged;
import lk.bookhive.auth.event.PasswordResetRequested;
import lk.bookhive.common.domain.Language;
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
 * committed, so we never email about something that wasn't actually saved.
 *
 * <p>English only for now; Sinhala/Tamil templates come in checklist step 357. Links already open
 * the website in the user's language.
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
    String link = link(event.language(), "verify-email", event.token());
    send(
        event.userId(),
        event.email(),
        "Verify your BookHive email",
        """
        Hello %s,

        Welcome to BookHive! Please confirm your email address by opening this link:

        %s

        If you didn't create a BookHive account, you can ignore this email.

        BookHive
        """
            .formatted(event.fullName(), link));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPasswordResetRequested(PasswordResetRequested event) {
    String link = link(event.language(), "reset-password", event.token());
    send(
        event.userId(),
        event.email(),
        "Reset your BookHive password",
        """
        Hello %s,

        We received a request to reset your BookHive password. Choose a new one here:

        %s

        The link works for one hour and only once. If you didn't ask for this, ignore this
        email: your password stays the same.

        BookHive
        """
            .formatted(event.fullName(), link));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPasswordChanged(PasswordChanged event) {
    String forgotLink =
        UriComponentsBuilder.fromUriString(properties.frontendUrl())
            .pathSegment(event.language().code(), "forgot-password")
            .toUriString();
    send(
        event.userId(),
        event.email(),
        "Your BookHive password was changed",
        """
        Hello %s,

        The password of your BookHive account was just changed, and you were logged out on
        your other devices.

        If this wasn't you, reset your password right away and contact the library:
        %s

        BookHive
        """
            .formatted(event.fullName(), forgotLink));
  }

  /** e.g. http://localhost:3000/si/reset-password?token=... */
  private String link(Language language, String page, String token) {
    return UriComponentsBuilder.fromUriString(properties.frontendUrl())
        .pathSegment(language.code(), page)
        .queryParam("token", token)
        .toUriString();
  }

  private void send(UUID userId, String to, String subject, String text) {
    try {
      emailSender.send(new EmailMessage(to, subject, text));
    } catch (RuntimeException e) {
      // The change is already saved; a failed email must not turn it into an error.
      // Reliable delivery with retries comes with the outbox (checklist step 355).
      log.error("Could not send \"{}\" email to user {}", subject, userId, e);
    }
  }
}
