package lk.bookhive.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.auth.config.AuthProperties;
import lk.bookhive.auth.config.JwtProperties;
import lk.bookhive.auth.domain.RefreshToken;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.domain.UserStatus;
import lk.bookhive.auth.repository.RefreshTokenRepository;
import lk.bookhive.auth.repository.UserRepository;
import lk.bookhive.auth.service.JwtService.AccessToken;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.ForbiddenException;
import lk.bookhive.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, refresh and logout (ADR 0001): a short access JWT plus a rotating refresh token. The
 * controller puts the refresh token in an httpOnly cookie; this class never sees HTTP.
 */
@Service
public class SessionService {

  private static final Logger log = LoggerFactory.getLogger(SessionService.class);

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final AuthProperties authProperties;
  private final AuditService audit;
  private final Clock clock;

  /**
   * Checked when the email doesn't exist, so a wrong email takes as long as a wrong password and
   * response times don't reveal which emails have accounts.
   */
  private final String dummyPasswordHash;

  public SessionService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      JwtProperties jwtProperties,
      AuthProperties authProperties,
      AuditService audit,
      Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.jwtProperties = jwtProperties;
    this.authProperties = authProperties;
    this.audit = audit;
    this.clock = clock;
    this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  /**
   * Email + password -> a new session (AUTH-3). Starts a new refresh token family.
   *
   * <p>Lockout (AUTH-6): after too many wrong passwords in a row the account is locked for a while.
   * {@code noRollbackFor}: a wrong password must still be counted even though login fails.
   */
  @Transactional(noRollbackFor = UnauthorizedException.class)
  public Session login(String email, String password) {
    Instant now = Instant.now(clock);
    Optional<User> found = users.findByEmail(User.normalizeEmail(email));
    String hash = found.map(User::getPasswordHash).orElse(dummyPasswordHash);
    boolean passwordMatches = passwordEncoder.matches(password, hash);

    if (found.isEmpty() || !passwordMatches) {
      // Not counted while already locked, so an attacker can't keep extending the lock
      found
          .filter(user -> !user.isLockedAt(now))
          .ifPresent(
              user -> {
                user.recordFailedLogin(
                    now, authProperties.maxFailedLogins(), authProperties.lockoutDuration());
                if (user.isLockedAt(now)) {
                  audit.record("auth.account_locked", null, "user", user.getId(), Map.of());
                  log.warn("User {} locked after too many failed logins", user.getId());
                }
              });
      audit.record(
          "auth.login_failed",
          null,
          "user",
          found.map(User::getId).orElse(null),
          Map.of("email", User.normalizeEmail(email), "accountExists", found.isPresent()));
      // Same answer for "no such email", "wrong password" and "locked + wrong password", so
      // attackers can't tell which emails have accounts
      throw new UnauthorizedException("INVALID_CREDENTIALS", "The email or password is incorrect.");
    }
    User user = found.get();

    // Everything below is only revealed to someone who knows the correct password
    if (user.isLockedAt(now)) {
      throw new ForbiddenException(
          "ACCOUNT_LOCKED",
          "Too many failed attempts. Please try again in "
              + minutesUntil(now, user.getLockedUntil())
              + " minutes, or reset your password.");
    }
    if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
      throw new ForbiddenException(
          "EMAIL_NOT_VERIFIED", "Please verify your email address before logging in.");
    }
    if (user.getStatus() == UserStatus.DISABLED) {
      throw new ForbiddenException("ACCOUNT_DISABLED", "This account has been disabled.");
    }

    user.recordSuccessfulLogin();
    audit.record("auth.login", user.getId(), "user", user.getId(), Map.of());
    log.info("User {} logged in", user.getId());
    return startNewSession(user);
  }

  private static long minutesUntil(Instant now, Instant until) {
    return Math.max(1, Duration.between(now, until).toSeconds() / 60 + 1);
  }

  /**
   * Swaps a refresh token for a new one plus a new access token (rotation). A token that was
   * already used means it was copied: the whole session is revoked (reuse detection).
   *
   * <p>{@code noRollbackFor}: when reuse is detected, the revocation must be saved even though the
   * request ends with an error.
   */
  @Transactional(noRollbackFor = UnauthorizedException.class)
  public Session refresh(String rawRefreshToken) {
    Instant now = Instant.now(clock);
    RefreshToken current =
        Optional.ofNullable(rawRefreshToken)
            .flatMap(raw -> refreshTokens.findByTokenHash(SecureTokens.hash(raw)))
            .orElseThrow(SessionService::invalidRefreshToken);

    if (current.getRevokedAt() != null) {
      int revoked = refreshTokens.revokeFamily(current.getFamilyId(), now);
      audit.record(
          "auth.refresh_token_reused",
          null,
          "user",
          current.getUser().getId(),
          Map.of("sessionsRevoked", revoked));
      log.warn(
          "Refresh token reuse for user {}: revoked {} token(s) of the session",
          current.getUser().getId(),
          revoked);
      throw invalidRefreshToken();
    }
    if (!current.isActive(now)) {
      throw invalidRefreshToken();
    }
    User user = current.getUser();
    if (user.getStatus() != UserStatus.ACTIVE) {
      refreshTokens.revokeFamily(current.getFamilyId(), now);
      throw invalidRefreshToken();
    }

    current.revoke(now);
    return startSession(user, current.getFamilyId());
  }

  /** Ends this session (its whole token family). Unknown or missing tokens are ignored. */
  @Transactional
  public void logout(String rawRefreshToken) {
    if (rawRefreshToken == null) {
      return;
    }
    refreshTokens
        .findByTokenHash(SecureTokens.hash(rawRefreshToken))
        .ifPresent(
            token -> {
              refreshTokens.revokeFamily(token.getFamilyId(), Instant.now(clock));
              UUID userId = token.getUser().getId();
              audit.record("auth.logout", userId, "user", userId, Map.of());
            });
  }

  /** A brand-new session (new token family), e.g. after login or a password change. */
  @Transactional
  public Session startNewSession(User user) {
    return startSession(user, UUID.randomUUID());
  }

  private Session startSession(User user, UUID familyId) {
    Instant now = Instant.now(clock);
    String rawRefreshToken = SecureTokens.generate();
    Instant refreshExpiresAt = now.plus(jwtProperties.refreshTokenTtl());
    refreshTokens.save(
        RefreshToken.issue(user, SecureTokens.hash(rawRefreshToken), familyId, refreshExpiresAt));

    AccessToken accessToken = jwtService.issueAccessToken(user.getId(), user.getRoles());
    return new Session(user, accessToken, rawRefreshToken, refreshExpiresAt);
  }

  private static UnauthorizedException invalidRefreshToken() {
    return new UnauthorizedException(
        "INVALID_REFRESH_TOKEN", "Your session has ended. Please log in again.");
  }

  /**
   * Everything a successful login/refresh produces.
   *
   * @param refreshToken raw value, for the cookie only; never put it in a response body or log
   */
  public record Session(
      User user, AccessToken accessToken, String refreshToken, Instant refreshExpiresAt) {

    @Override
    public String toString() {
      return "Session[userId=" + user.getId() + "]";
    }
  }
}
