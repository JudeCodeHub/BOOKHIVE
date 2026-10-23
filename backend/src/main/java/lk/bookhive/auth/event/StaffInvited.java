package lk.bookhive.auth.event;

import java.util.UUID;
import lk.bookhive.auth.domain.Role;
import lk.bookhive.common.domain.Language;

/**
 * Published when an admin creates a staff account: the person gets an invite email to set their
 * password.
 *
 * @param token the raw one-time token for the link (only its hash is in the database)
 */
public record StaffInvited(
    UUID userId, String email, String fullName, Language language, Role role, String token) {

  // Never print the token into logs
  @Override
  public String toString() {
    return "StaffInvited[userId=" + userId + ", role=" + role + "]";
  }
}
