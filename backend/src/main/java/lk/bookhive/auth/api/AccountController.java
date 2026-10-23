package lk.bookhive.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lk.bookhive.auth.api.PasswordRequests.ChangePasswordRequest;
import lk.bookhive.auth.api.ProfileDtos.ProfileResponse;
import lk.bookhive.auth.api.ProfileDtos.UpdateProfileRequest;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.domain.Language;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The logged-in user's own account, under /api/v1/me (needs a valid access token). The user id
 * always comes from the token, never from the request, so nobody can read or edit someone else's
 * account through these endpoints.
 */
@RestController
@RequestMapping("/me")
@Tag(name = "My account")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AccountController {

  private final AuthService authService;
  private final RefreshCookies refreshCookies;

  public AccountController(AuthService authService, RefreshCookies refreshCookies) {
    this.authService = authService;
    this.refreshCookies = refreshCookies;
  }

  @GetMapping
  @Operation(summary = "My profile")
  public ProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
    return ProfileResponse.from(authService.getProfile(userId(jwt)));
  }

  @PatchMapping
  @Operation(summary = "Edit my name, phone or language (only the fields sent change)")
  public ProfileResponse updateProfile(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
    Language language =
        request.preferredLanguage() == null ? null : Language.fromCode(request.preferredLanguage());
    return ProfileResponse.from(
        authService.updateProfile(userId(jwt), request.fullName(), request.phone(), language));
  }

  /**
   * Logs out every other device; this one gets a fresh session (new access token + cookie), so the
   * user stays logged in here.
   */
  @PostMapping("/password")
  @Operation(summary = "Change password (needs the current password)")
  public ResponseEntity<AuthResponse> changePassword(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
    return refreshCookies.sessionResponse(
        authService.changePassword(userId(jwt), request.currentPassword(), request.newPassword()));
  }

  /** The token's "sub" claim is the user id (see JwtService). */
  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
