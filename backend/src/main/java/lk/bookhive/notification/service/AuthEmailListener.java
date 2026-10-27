package lk.bookhive.notification.service;

import java.util.Map;
import lk.bookhive.auth.event.EmailVerificationRequested;
import lk.bookhive.auth.event.PasswordChanged;
import lk.bookhive.auth.event.PasswordResetRequested;
import lk.bookhive.auth.event.StaffInvited;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Account emails: verify email, reset password, password changed, staff invite. Sent only after the
 * change is saved, in the user's language, through the outbox (ADR 0004). The one-time tokens
 * travel only inside the link, never in logs.
 */
@Component
public class AuthEmailListener {

  private final Notifier notifier;

  public AuthEmailListener(Notifier notifier) {
    this.notifier = notifier;
  }

  @NotificationListener
  public void onEmailVerificationRequested(EmailVerificationRequested event) {
    notifier.send(
        new NotificationRequest(
            event.userId(),
            NotificationType.EMAIL_VERIFICATION,
            Map.of(),
            withToken("/verify-email", event.token()),
            null));
  }

  @NotificationListener
  public void onPasswordResetRequested(PasswordResetRequested event) {
    notifier.send(
        new NotificationRequest(
            event.userId(),
            NotificationType.PASSWORD_RESET,
            Map.of(),
            withToken("/reset-password", event.token()),
            null));
  }

  @NotificationListener
  public void onPasswordChanged(PasswordChanged event) {
    notifier.send(
        new NotificationRequest(
            event.userId(), NotificationType.PASSWORD_CHANGED, Map.of(), "/forgot-password", null));
  }

  @NotificationListener
  public void onStaffInvited(StaffInvited event) {
    notifier.send(
        new NotificationRequest(
            event.userId(),
            NotificationType.STAFF_INVITE,
            // The template translates it (email.role.LIBRARIAN / ADMIN)
            Map.of("role", event.role().name()),
            withToken("/accept-invite", event.token()),
            null));
  }

  /** "/reset-password?token=..." (the notifier adds the site address and language). */
  private static String withToken(String page, String token) {
    return UriComponentsBuilder.fromPath(page).queryParam("token", token).build().toUriString();
  }
}
