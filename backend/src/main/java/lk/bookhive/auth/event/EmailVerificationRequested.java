package lk.bookhive.auth.event;

import java.util.UUID;
import lk.bookhive.common.domain.Language;

/**
 * Published when a user needs a "verify your email" message (after registering). The notification
 * module listens and sends the email; auth doesn't know how emails are sent.
 *
 * @param token the raw one-time token for the link (only its hash is in the database)
 */
public record EmailVerificationRequested(
    UUID userId, String email, String fullName, Language language, String token) {

  // Never print the token into logs
  @Override
  public String toString() {
    return "EmailVerificationRequested[userId=" + userId + ", language=" + language + "]";
  }
}
