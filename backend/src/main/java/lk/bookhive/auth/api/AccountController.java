package lk.bookhive.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lk.bookhive.auth.api.PasswordRequests.ChangePasswordRequest;
import lk.bookhive.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The logged-in user's own account, under /api/v1/me (needs a valid access token). Profile
 * endpoints (GET/PATCH /me) come in checklist step 98.
 */
@RestController
@RequestMapping("/me")
@Tag(name = "My account")
@SecurityRequirement(name = "bearer")
public class AccountController {

  private final AuthService authService;
  private final RefreshCookies refreshCookies;

  public AccountController(AuthService authService, RefreshCookies refreshCookies) {
    this.authService = authService;
    this.refreshCookies = refreshCookies;
  }

  /**
   * Logs out every other device; this one gets a fresh session (new access token + cookie), so the
   * user stays logged in here.
   */
  @PostMapping("/password")
  @Operation(summary = "Change password (needs the current password)")
  public ResponseEntity<AuthResponse> changePassword(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
    UUID userId = UUID.fromString(jwt.getSubject());
    return refreshCookies.sessionResponse(
        authService.changePassword(userId, request.currentPassword(), request.newPassword()));
  }
}
