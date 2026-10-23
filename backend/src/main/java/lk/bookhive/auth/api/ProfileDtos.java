package lk.bookhive.auth.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lk.bookhive.auth.domain.Role;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.domain.UserStatus;

/** Request and response of {@code GET/PATCH /api/v1/me}. */
final class ProfileDtos {

  private ProfileDtos() {}

  /** The logged-in user's own account. */
  record ProfileResponse(
      UUID id,
      String email,
      String fullName,
      String phone,
      String preferredLanguage,
      Set<Role> roles,
      UserStatus status,
      Instant emailVerifiedAt,
      Instant createdAt) {

    static ProfileResponse from(User user) {
      return new ProfileResponse(
          user.getId(),
          user.getEmail(),
          user.getFullName(),
          user.getPhone(),
          user.getPreferredLanguage().code(),
          user.getRoles(),
          user.getStatus(),
          user.getEmailVerifiedAt(),
          user.getCreatedAt());
    }
  }

  /**
   * Only the fields that are sent are changed (PATCH). An empty phone ("") removes the phone.
   *
   * @param fullName optional; if sent, must contain non-space characters
   */
  record UpdateProfileRequest(
      @Size(min = 1, max = 150) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
          String fullName,
      @Pattern(regexp = "^$|^(\\+94|0)[0-9]{9}$", message = "must be a Sri Lankan phone number")
          String phone,
      @Pattern(regexp = "^(en|si|ta)$", message = "must be en, si or ta")
          String preferredLanguage) {}
}
