package lk.bookhive.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.auth.config.AuthProperties;
import lk.bookhive.auth.domain.Role;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.domain.UserStatus;
import lk.bookhive.auth.domain.VerificationToken;
import lk.bookhive.auth.domain.VerificationTokenType;
import lk.bookhive.auth.event.EmailVerificationRequested;
import lk.bookhive.auth.event.PasswordChanged;
import lk.bookhive.auth.event.PasswordResetRequested;
import lk.bookhive.auth.event.StaffInvited;
import lk.bookhive.auth.repository.RefreshTokenRepository;
import lk.bookhive.auth.repository.UserRepository;
import lk.bookhive.auth.repository.VerificationTokenRepository;
import lk.bookhive.auth.service.SessionService.Session;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lifecycle: registration, email verification and passwords (AUTH-1, 2, 4, 5). Logged-in
 * sessions are handled by {@link SessionService}.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository users;
  private final VerificationTokenRepository verificationTokens;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final SessionService sessionService;
  private final AuditService audit;
  private final ApplicationEventPublisher events;
  private final AuthProperties properties;
  private final Clock clock;

  public AuthService(
      UserRepository users,
      VerificationTokenRepository verificationTokens,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      SessionService sessionService,
      AuditService audit,
      ApplicationEventPublisher events,
      AuthProperties properties,
      Clock clock) {
    this.users = users;
    this.verificationTokens = verificationTokens;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.sessionService = sessionService;
    this.audit = audit;
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
    passwordPolicy.check(password, "password", normalizedEmail);

    User user =
        users.save(
            User.register(
                normalizedEmail, passwordEncoder.encode(password), fullName, phone, language));
    String token = issueToken(user, VerificationTokenType.EMAIL);
    // Delivered after the transaction commits, so no email goes out if saving fails
    events.publishEvent(
        new EmailVerificationRequested(
            user.getId(), user.getEmail(), user.getFullName(), user.getPreferredLanguage(), token));
    audit.record("auth.registered", user.getId(), "user", user.getId(), Map.of());
    log.info("Registered user {}", user.getId());
    return user;
  }

  /** The user clicked the link in the verification email. Each link works once. */
  @Transactional
  public void verifyEmail(String token) {
    Instant now = Instant.now(clock);
    VerificationToken verification = useToken(token, VerificationTokenType.EMAIL, now);
    verification.getUser().verifyEmail(now);
    audit.record(
        "auth.email_verified",
        verification.getUser().getId(),
        "user",
        verification.getUser().getId(),
        Map.of());
    log.info("Verified email of user {}", verification.getUser().getId());
  }

  /**
   * "Forgot password" (AUTH-4): emails a reset link if the account exists. The caller always gets
   * the same answer, so this can't be used to find out which emails have accounts.
   */
  @Transactional
  public void requestPasswordReset(String email) {
    users
        .findByEmail(User.normalizeEmail(email))
        .filter(user -> user.getStatus() != UserStatus.DISABLED)
        .ifPresentOrElse(
            user -> {
              // Only the newest reset email works
              verificationTokens.invalidateUnused(
                  user.getId(), VerificationTokenType.RESET, Instant.now(clock));
              String token = issueToken(user, VerificationTokenType.RESET);
              events.publishEvent(
                  new PasswordResetRequested(
                      user.getId(),
                      user.getEmail(),
                      user.getFullName(),
                      user.getPreferredLanguage(),
                      token));
              audit.record("auth.password_reset_requested", null, "user", user.getId(), Map.of());
              log.info("Password reset requested for user {}", user.getId());
            },
            () -> log.info("Password reset requested for an unknown or disabled email"));
  }

  /**
   * Sets a new password from a reset link. Logs out every session, so an attacker who had access is
   * locked out too. Opening the link also proves the email address, so it counts as verified.
   */
  @Transactional
  public void resetPassword(String token, String newPassword) {
    Instant now = Instant.now(clock);
    User user = useToken(token, VerificationTokenType.RESET, now).getUser();
    // If this fails, the transaction rolls back and the link can be used again with a better one
    passwordPolicy.check(newPassword, "newPassword", user.getEmail());

    user.changePassword(passwordEncoder.encode(newPassword));
    user.verifyEmail(now);
    user.recordSuccessfulLogin(); // a locked-out owner can get back in by resetting
    refreshTokens.revokeAllForUser(user.getId(), now);
    publishPasswordChanged(user);
    audit.record("auth.password_reset", user.getId(), "user", user.getId(), Map.of());
    log.info("Password reset for user {}", user.getId());
  }

  /** The logged-in user's own account (GET /me). */
  @Transactional(readOnly = true)
  public User getProfile(UUID userId) {
    return requireUser(userId);
  }

  /** Edits name, phone and/or language (PATCH /me). Null arguments are left unchanged. */
  @Transactional
  public User updateProfile(UUID userId, String fullName, String phone, Language language) {
    User user = requireUser(userId);
    user.updateProfile(fullName, phone, language);
    // Which fields changed, not their values (keeps personal data out of the audit log)
    Map<String, Object> changed = new LinkedHashMap<>();
    changed.put("fullName", fullName != null);
    changed.put("phone", phone != null);
    changed.put("preferredLanguage", language != null);
    audit.record("auth.profile_updated", userId, "user", userId, changed);
    return user;
  }

  /**
   * Changes the password of a logged-in user (AUTH-5). All other sessions are logged out; this
   * device gets a fresh session so the user stays logged in here.
   */
  @Transactional
  public Session changePassword(UUID userId, String currentPassword, String newPassword) {
    User user = requireUser(userId);
    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new BusinessRuleException(
          "CURRENT_PASSWORD_INCORRECT", "Your current password is incorrect.");
    }
    passwordPolicy.check(newPassword, "newPassword", user.getEmail());

    user.changePassword(passwordEncoder.encode(newPassword));
    refreshTokens.revokeAllForUser(user.getId(), Instant.now(clock));
    publishPasswordChanged(user);
    audit.record("auth.password_changed", user.getId(), "user", user.getId(), Map.of());
    log.info("Password changed for user {}", user.getId());
    return sessionService.startNewSession(user);
  }

  // A valid token for a deleted account is the only way to get here
  private User requireUser(UUID userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Account not found."));
  }

  /** Creates a one-time email link token; returns the raw token for the email. */
  private String issueToken(User user, VerificationTokenType type) {
    String token = SecureTokens.generate();
    verificationTokens.save(
        VerificationToken.issue(
            user, type, SecureTokens.hash(token), Instant.now(clock).plus(ttl(type))));
    return token;
  }

  /**
   * An admin creates a staff account (PRD ADM-4). The person gets an invite email and chooses their
   * own password, so no one else ever knows it.
   */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public User inviteStaff(
      UUID adminId, String email, String fullName, String phone, Language language, Role role) {
    String normalizedEmail = User.normalizeEmail(email);
    if (users.existsByEmail(normalizedEmail)) {
      throw new ConflictException(
          "EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
    }

    // Placeholder password: random and never shown, so the account can't be used before the invite
    String unusableHash = passwordEncoder.encode(SecureTokens.generate());
    User user =
        users.save(
            User.inviteStaff(normalizedEmail, unusableHash, fullName, phone, language, role));
    String token = issueToken(user, VerificationTokenType.INVITE);
    events.publishEvent(
        new StaffInvited(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getPreferredLanguage(),
            role,
            token));
    audit.record("auth.staff_invited", adminId, "user", user.getId(), Map.of("role", role.name()));
    log.info("Admin {} invited {} {}", adminId, role, user.getId());
    return user;
  }

  /**
   * The invited staff member chose a password through the invite link: the account becomes ACTIVE
   * (the link proves the email address) and they can log in.
   */
  @Transactional
  public void acceptInvite(String token, String password) {
    Instant now = Instant.now(clock);
    User user = useToken(token, VerificationTokenType.INVITE, now).getUser();
    passwordPolicy.check(password, "password", user.getEmail());

    user.changePassword(passwordEncoder.encode(password));
    user.verifyEmail(now);
    audit.record("auth.staff_invite_accepted", user.getId(), "user", user.getId(), Map.of());
    log.info("Staff member {} accepted the invite", user.getId());
  }

  /** Finds an unused, unexpired link token of this type and marks it used. */
  private VerificationToken useToken(String rawToken, VerificationTokenType type, Instant now) {
    String prefix =
        switch (type) {
          case EMAIL -> "VERIFICATION_TOKEN";
          case RESET -> "RESET_TOKEN";
          case INVITE -> "INVITE_TOKEN";
        };
    VerificationToken token =
        verificationTokens
            .findByTokenHashAndType(SecureTokens.hash(rawToken), type)
            .filter(t -> !t.isUsed())
            .orElseThrow(
                () -> new BusinessRuleException(prefix + "_INVALID", "This link is not valid."));
    if (token.isExpired(now)) {
      throw new BusinessRuleException(prefix + "_EXPIRED", "This link has expired.");
    }
    token.markUsed(now);
    return token;
  }

  private Duration ttl(VerificationTokenType type) {
    return switch (type) {
      case EMAIL -> properties.emailVerificationTtl();
      case RESET -> properties.passwordResetTtl();
      case INVITE -> properties.staffInviteTtl();
    };
  }

  private void publishPasswordChanged(User user) {
    events.publishEvent(
        new PasswordChanged(
            user.getId(), user.getEmail(), user.getFullName(), user.getPreferredLanguage()));
  }
}
