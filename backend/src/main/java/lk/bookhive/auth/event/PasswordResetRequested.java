package lk.bookhive.auth.event;

import java.util.UUID;
import lk.bookhive.common.domain.Language;

/**
 * Published when someone asks for a "reset your password" email for an existing account.
 *
 * @param token the raw one-time token for the link (only its hash is in the database)
 */
public record PasswordResetRequested(
    UUID userId, String email, String fullName, Language language, String token) {

  // Never print the token into logs
  @Override
  public String toString() {
    return "PasswordResetRequested[userId=" + userId + ", language=" + language + "]";
  }
}
