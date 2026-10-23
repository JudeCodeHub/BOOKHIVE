package lk.bookhive.auth.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lk.bookhive.auth.domain.Role;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.SessionService.Session;

/**
 * Response of login and refresh. The refresh token is NOT here: it's in the httpOnly cookie.
 *
 * @param accessToken send as "Authorization: Bearer ..." until {@code expiresAt}
 */
public record AuthResponse(
    String accessToken, String tokenType, Instant expiresAt, UserSummary user) {

  static AuthResponse from(Session session) {
    return new AuthResponse(
        session.accessToken().value(),
        "Bearer",
        session.accessToken().expiresAt(),
        UserSummary.from(session.user()));
  }

  /** What the frontend needs to show "logged in as ..." and the right menus. */
  public record UserSummary(
      UUID id, String email, String fullName, Set<Role> roles, String preferredLanguage) {

    static UserSummary from(User user) {
      return new UserSummary(
          user.getId(),
          user.getEmail(),
          user.getFullName(),
          user.getRoles(),
          user.getPreferredLanguage().code());
    }
  }
}
