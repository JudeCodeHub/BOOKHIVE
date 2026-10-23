package lk.bookhive.auth.api;

import java.time.Duration;
import lk.bookhive.auth.config.AuthProperties;
import lk.bookhive.auth.config.JwtProperties;
import lk.bookhive.auth.service.SessionService.Session;
import lk.bookhive.common.config.WebConfig;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Builds the httpOnly refresh-token cookie (ADR 0001) and the login-style response that carries it.
 * Used by every endpoint that starts a session: login, refresh, change password.
 */
@Component
class RefreshCookies {

  /** The httpOnly cookie holding the refresh token. */
  static final String NAME = "bookhive_refresh";

  // The browser sends the cookie only to the auth endpoints, never to the rest of the API
  private static final String PATH = WebConfig.API_PREFIX + "/auth";

  private final AuthProperties authProperties;
  private final JwtProperties jwtProperties;

  RefreshCookies(AuthProperties authProperties, JwtProperties jwtProperties) {
    this.authProperties = authProperties;
    this.jwtProperties = jwtProperties;
  }

  /** 200 with the access token in the body and the refresh token in the cookie. */
  ResponseEntity<AuthResponse> sessionResponse(Session session) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            cookie(session.refreshToken(), jwtProperties.refreshTokenTtl()).toString())
        // Tokens must never be stored by a browser or proxy cache
        .cacheControl(CacheControl.noStore())
        .body(AuthResponse.from(session));
  }

  /** A Set-Cookie value that deletes the cookie (logout). */
  String cleared() {
    return cookie("", Duration.ZERO).toString();
  }

  private ResponseCookie cookie(String value, Duration maxAge) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true) // JavaScript can't read it, so XSS can't steal it
        .secure(authProperties.refreshCookieSecure()) // HTTPS only (except local dev)
        .sameSite("Strict") // not sent on requests started by other websites
        .path(PATH)
        .maxAge(maxAge)
        .build();
  }
}
