package lk.bookhive.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import lk.bookhive.auth.config.AuthProperties;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.domain.VerificationToken;
import lk.bookhive.auth.domain.VerificationTokenType;
import lk.bookhive.auth.event.EmailVerificationRequested;
import lk.bookhive.auth.repository.UserRepository;
import lk.bookhive.auth.repository.VerificationTokenRepository;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and email verification (AUTH-1, AUTH-2). Login comes in the next steps. */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  /** BCrypt only looks at the first 72 bytes; longer passwords would be silently cut off. */
  static final int MAX_PASSWORD_BYTES = 72;

  private final UserRepository users;
  private final VerificationTokenRepository verificationTokens;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher events;
  private final AuthProperties properties;
  private final Clock clock;

  public AuthService(
      UserRepository users,
      VerificationTokenRepository verificationTokens,
      PasswordEncoder passwordEncoder,
      ApplicationEventPublisher events,
      AuthProperties properties,
      Clock clock) {
    this.users = users;
    this.verificationTokens = verificationTokens;
    this.passwordEncoder = passwordEncoder;
    this.events = events;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Creates an unverified MEMBER account and emails a verification link. The user can log in only
   * after clicking it.
   */
  @Transactional
  public User register(
      String email, String password, String fullName, String phone, Language language) {
    String normalizedEmail = User.normalizeEmail(email);
    if (users.existsByEmail(normalizedEmail)) {
      throw new ConflictException(
          "EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
    }
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new BusinessRuleException("PASSWORD_TOO_LONG", "Please choose a shorter password.");
    }

    User user =
        users.save(
            User.register(
                normalizedEmail, passwordEncoder.encode(password), fullName, phone, language));
    sendVerificationEmail(user);
    log.info("Registered user {}", user.getId());
    return user;
  }

  /**
   * The user clicked the link in the verification email. Each link works once, until it expires.
   */
  @Transactional
  public void verifyEmail(String token) {
    Instant now = Instant.now(clock);
    VerificationToken verification =
        verificationTokens
            .findByTokenHashAndType(SecureTokens.hash(token), VerificationTokenType.EMAIL)
            .filter(t -> !t.isUsed())
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        "VERIFICATION_TOKEN_INVALID", "This verification link is not valid."));
    if (verification.isExpired(now)) {
      throw new BusinessRuleException(
          "VERIFICATION_TOKEN_EXPIRED", "This verification link has expired.");
    }

    verification.markUsed(now);
    verification.getUser().verifyEmail(now);
    log.info("Verified email of user {}", verification.getUser().getId());
  }

  private void sendVerificationEmail(User user) {
    String token = SecureTokens.generate();
    verificationTokens.save(
        VerificationToken.issue(
            user,
            VerificationTokenType.EMAIL,
            SecureTokens.hash(token),
            Instant.now(clock).plus(properties.emailVerificationTtl())));
    // Delivered after the transaction commits, so no email goes out if saving fails
    events.publishEvent(
        new EmailVerificationRequested(
            user.getId(), user.getEmail(), user.getFullName(), user.getPreferredLanguage(), token));
  }
}
