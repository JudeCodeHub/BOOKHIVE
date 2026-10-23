package lk.bookhive.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.bookhive.auth.api.PasswordRequests.AcceptInviteRequest;
import lk.bookhive.auth.api.PasswordRequests.ForgotPasswordRequest;
import lk.bookhive.auth.api.PasswordRequests.ResetPasswordRequest;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.auth.service.SessionService;
import lk.bookhive.common.domain.Language;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public account endpoints under /api/v1/auth (no login needed, see SecurityConfig). */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Register, verify email, login, refresh, logout, passwords")
public class AuthController {

  /** The httpOnly cookie holding the refresh token (ADR 0001). */
  public static final String REFRESH_COOKIE = RefreshCookies.NAME;

  private final AuthService authService;
  private final SessionService sessionService;
  private final RefreshCookies refreshCookies;

  public AuthController(
      AuthService authService, SessionService sessionService, RefreshCookies refreshCookies) {
    this.authService = authService;
    this.sessionService = sessionService;
    this.refreshCookies = refreshCookies;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create an account; a verification link is emailed")
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    Language language =
        request.preferredLanguage() == null
            ? Language.EN
            : Language.fromCode(request.preferredLanguage());
    User user =
        authService.register(
            request.email(), request.password(), request.fullName(), request.phone(), language);
    return new RegisterResponse(user.getId(), user.getEmail(), user.getStatus());
  }

  @PostMapping("/verify-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Confirm the email address with the token from the email link")
  public void verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    authService.verifyEmail(request.token());
  }

  @PostMapping("/login")
  @Operation(summary = "Log in: access token in the body, refresh token in an httpOnly cookie")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return refreshCookies.sessionResponse(
        sessionService.login(request.email(), request.password()));
  }

  @PostMapping("/refresh")
  @Operation(summary = "New access token from the refresh cookie (the cookie is rotated)")
  public ResponseEntity<AuthResponse> refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
    return refreshCookies.sessionResponse(sessionService.refresh(refreshToken));
  }

  @PostMapping("/logout")
  @Operation(summary = "End this session and clear the refresh cookie")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
    sessionService.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookies.cleared())
        .build();
  }

  /** Always 204, whether or not the email has an account (so it can't be used to check). */
  @PostMapping("/forgot-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Email a password reset link if the account exists")
  public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    authService.requestPasswordReset(request.email());
  }

  @PostMapping("/accept-invite")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "New staff member: set your password with the token from the invite email")
  public void acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
    authService.acceptInvite(request.token(), request.password());
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Set a new password with the token from the reset email")
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request.token(), request.newPassword());
  }
}
