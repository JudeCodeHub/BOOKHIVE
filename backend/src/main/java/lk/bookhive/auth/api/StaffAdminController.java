package lk.bookhive.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import lk.bookhive.auth.domain.Role;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.domain.UserStatus;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.domain.Language;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff accounts, admins only (/api/v1/admin/**, see SecurityConfig). Listing, role changes and
 * deactivation come with the admin pages (checklist step 357).
 */
@RestController
@RequestMapping("/admin/staff")
@Tag(name = "Admin: staff accounts")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffAdminController {

  private final AuthService authService;

  public StaffAdminController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a librarian or admin account; they get an invite email")
  public StaffResponse invite(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InviteStaffRequest request) {
    Language language =
        request.preferredLanguage() == null
            ? Language.EN
            : Language.fromCode(request.preferredLanguage());
    User user =
        authService.inviteStaff(
            UUID.fromString(jwt.getSubject()),
            request.email(),
            request.fullName(),
            request.phone(),
            language,
            Role.valueOf(request.role()));
    return new StaffResponse(
        user.getId(), user.getEmail(), user.getFullName(), user.getRoles(), user.getStatus());
  }

  /**
   * @param role LIBRARIAN or ADMIN
   */
  record InviteStaffRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 150) String fullName,
      @Pattern(regexp = "^(\\+94|0)[0-9]{9}$", message = "must be a Sri Lankan phone number")
          String phone,
      @NotNull @Pattern(regexp = "^(LIBRARIAN|ADMIN)$", message = "must be LIBRARIAN or ADMIN")
          String role,
      @Pattern(regexp = "^(en|si|ta)$", message = "must be en, si or ta")
          String preferredLanguage) {}

  /** The new account: PENDING_VERIFICATION until the invite is accepted. */
  record StaffResponse(
      UUID id, String email, String fullName, Set<Role> roles, UserStatus status) {}
}
