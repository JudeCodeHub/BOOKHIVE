package lk.bookhive.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import lk.bookhive.auth.config.AuthProperties;
import lk.bookhive.auth.config.JwtProperties;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.auth.service.SessionService;
import lk.bookhive.auth.service.SessionService.Session;
import lk.bookhive.common.config.WebConfig;
import lk.bookhive.common.domain.Language;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
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
@Tag(name = "Auth", description = "Register, verify email, login, refresh, logout")
public class AuthController {

  /** The httpOnly cookie holding the refresh token (ADR 0001). */
  public static final String REFRESH_COOKIE = "bookhive_refresh";

  // The browser sends the cookie only to the auth endpoints, never to the rest of the API
  private static final String REFRESH_COOKIE_PATH = WebConfig.API_PREFIX + "/auth";

  private final AuthService authService;
  private final SessionService sessionService;
  private final AuthProperties authProperties;
  private final JwtProperties jwtProperties;

  public AuthController(
      AuthService authService,
      SessionService sessionService,
      AuthProperties authProperties,
      JwtProperties jwtProperties) {
    this.authService = authService;
    this.sessionService = sessionService;
    this.authProperties = authProperties;
    this.jwtProperties = jwtProperties;
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
    return sessionResponse(sessionService.login(request.email(), request.password()));
  }

  @PostMapping("/refresh")
  @Operation(summary = "New access token from the refresh cookie (the cookie is rotated)")
  public ResponseEntity<AuthResponse> refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
    return sessionResponse(sessionService.refresh(refreshToken));
  }

  @PostMapping("/logout")
  @Operation(summary = "End this session and clear the refresh cookie")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
    sessionService.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
        .build();
  }

  private ResponseEntity<AuthResponse> sessionResponse(Session session) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            refreshCookie(session.refreshToken(), jwtProperties.refreshTokenTtl()).toString())
        // Tokens must never be stored by a browser or proxy cache
        .cacheControl(CacheControl.noStore())
        .body(AuthResponse.from(session));
  }

  /** maxAge 0 deletes the cookie (logout). */
  private ResponseCookie refreshCookie(String value, Duration maxAge) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true) // JavaScript can't read it, so XSS can't steal it
        .secure(authProperties.refreshCookieSecure()) // HTTPS only (except local dev)
        .sameSite("Strict") // not sent on requests started by other websites
        .path(REFRESH_COOKIE_PATH)
        .maxAge(maxAge)
        .build();
  }
}
