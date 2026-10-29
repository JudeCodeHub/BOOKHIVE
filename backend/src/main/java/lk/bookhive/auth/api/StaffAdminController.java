package lk.bookhive.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff accounts, admins only (/api/v1/admin/**, see SecurityConfig): invite, list, change role,
 * deactivate and reactivate, resend the invite (ADM-4, checklist step 358).
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
  public StaffSummary invite(
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
    return StaffSummary.from(user);
  }

  @GetMapping
  @Operation(summary = "All librarians and admins by name, deactivated ones included")
  public List<StaffSummary> list() {
    return authService.staff().stream().map(StaffSummary::from).toList();
  }

  @PutMapping("/{id}/role")
  @Operation(
      summary =
          "Make someone a librarian or an admin (not yourself; never the last active admin)."
              + " Their sessions end so the new role applies at once")
  public StaffSummary changeRole(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody ChangeRoleRequest request) {
    return StaffSummary.from(
        authService.changeStaffRole(adminId(jwt), id, Role.valueOf(request.role())));
  }

  @PostMapping("/{id}/deactivate")
  @Operation(summary = "Switch an account off: logged out everywhere, can't log in")
  public StaffSummary deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return StaffSummary.from(authService.deactivateStaff(adminId(jwt), id));
  }

  @PostMapping("/{id}/reactivate")
  @Operation(summary = "Switch an account back on")
  public StaffSummary reactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return StaffSummary.from(authService.reactivateStaff(adminId(jwt), id));
  }

  @PostMapping("/{id}/resend-invite")
  @Operation(summary = "Send a new invite email to someone who hasn't accepted theirs")
  public StaffSummary resendInvite(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return StaffSummary.from(authService.resendInvite(adminId(jwt), id));
  }

  private static UUID adminId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
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

  /**
   * @param role LIBRARIAN or ADMIN
   */
  public record ChangeRoleRequest(
      @NotNull @Pattern(regexp = "^(LIBRARIAN|ADMIN)$", message = "must be LIBRARIAN or ADMIN")
          String role) {}

  /**
   * A staff account.
   *
   * @param status PENDING_VERIFICATION until they accept the invite; DISABLED when switched off
   * @param invitedAt when the account was created
   * @param joinedAt when they accepted the invite; null until then
   */
  public record StaffSummary(
      UUID id,
      String email,
      String fullName,
      @Schema(nullable = true) String phone,
      Role role,
      UserStatus status,
      Instant invitedAt,
      @Schema(nullable = true) Instant joinedAt) {

    static StaffSummary from(User user) {
      return new StaffSummary(
          user.getId(),
          user.getEmail(),
          user.getFullName(),
          user.getPhone(),
          user.getStaffRole(),
          user.getStatus(),
          user.getCreatedAt(),
          user.getEmailVerifiedAt());
    }
  }
}
