package lk.bookhive.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.auth.config.JwtProperties;
import lk.bookhive.auth.domain.RefreshToken;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.domain.UserStatus;
import lk.bookhive.auth.repository.RefreshTokenRepository;
import lk.bookhive.auth.repository.UserRepository;
import lk.bookhive.auth.service.JwtService.AccessToken;
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
      Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.jwtProperties = jwtProperties;
    this.clock = clock;
    this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  /** Email + password -> a new session (AUTH-3). Starts a new refresh token family. */
  @Transactional
  public Session login(String email, String password) {
    Optional<User> found = users.findByEmail(User.normalizeEmail(email));
    String hash = found.map(User::getPasswordHash).orElse(dummyPasswordHash);
    boolean passwordMatches = passwordEncoder.matches(password, hash);

    // Same answer for "no such email" and "wrong password": don't help attackers find accounts
    User user =
        found
            .filter(u -> passwordMatches)
            .orElseThrow(
                () ->
                    new UnauthorizedException(
                        "INVALID_CREDENTIALS", "The email or password is incorrect."));

    // Only revealed after the correct password, so it can't be used to probe emails
    if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
      throw new ForbiddenException(
          "EMAIL_NOT_VERIFIED", "Please verify your email address before logging in.");
    }
    if (user.getStatus() == UserStatus.DISABLED) {
      throw new ForbiddenException("ACCOUNT_DISABLED", "This account has been disabled.");
    }

    log.info("User {} logged in", user.getId());
    return startSession(user, UUID.randomUUID());
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
        .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), Instant.now(clock)));
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
