package lk.bookhive.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import lk.bookhive.common.exception.FieldValidationException;
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

  /** Several accounts at once by id, e.g. names and emails for the staff application queue. */
  @Transactional(readOnly = true)
  public Map<UUID, User> findUsers(Collection<UUID> userIds) {
    return users.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));
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

  /** Staff accounts (step 358): librarians and admins, by name, deactivated ones included. */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public List<User> staff() {
    return users.findByAnyRole(List.of(Role.LIBRARIAN, Role.ADMIN));
  }

  /**
   * Librarian <-> admin. Their sessions end, so the new role applies at once (a demoted admin's
   * current access token still works for at most its last 15 minutes).
   */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public User changeStaffRole(UUID adminId, UUID userId, Role role) {
    User user = requireStaff(userId);
    Role old = user.getStaffRole();
    if (old == role) {
      return user;
    }
    notYourself(adminId, userId, "role");
    if (old == Role.ADMIN) {
      notLastAdmin(user, "role");
    }
    user.changeStaffRole(role);
    endSessions(user);
    audit.record(
        "auth.staff_role_changed",
        adminId,
        "user",
        userId,
        Map.of("from", old.name(), "to", role.name()));
    log.info("Admin {} changed {} from {} to {}", adminId, userId, old, role);
    return user;
  }

  /** The person left or shouldn't sign in: they're logged out everywhere and can't log in. */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public User deactivateStaff(UUID adminId, UUID userId) {
    User user = requireStaff(userId);
    if (user.getStatus() == UserStatus.DISABLED) {
      return user;
    }
    notYourself(adminId, userId, "status");
    notLastAdmin(user, "status");
    user.disable();
    endSessions(user);
    audit.record("auth.staff_deactivated", adminId, "user", userId, Map.of());
    log.info("Admin {} deactivated staff account {}", adminId, userId);
    return user;
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public User reactivateStaff(UUID adminId, UUID userId) {
    User user = requireStaff(userId);
    if (user.getStatus() != UserStatus.DISABLED) {
      return user;
    }
    user.enable();
    audit.record("auth.staff_reactivated", adminId, "user", userId, Map.of());
    return user;
  }

  /** A new invite email for someone who hasn't accepted theirs yet (the link lasts 72 h). */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public User resendInvite(UUID adminId, UUID userId) {
    User user = requireStaff(userId);
    if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
      throw new BusinessRuleException(
          "INVITE_ALREADY_ACCEPTED", "This person has already set up their account.");
    }
    String token = issueToken(user, VerificationTokenType.INVITE);
    events.publishEvent(
        new StaffInvited(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getPreferredLanguage(),
            user.getStaffRole(),
            token));
    audit.record("auth.staff_invite_resent", adminId, "user", userId, Map.of());
    return user;
  }

  private User requireStaff(UUID userId) {
    User user = requireUser(userId);
    if (user.getStaffRole() == null) {
      throw new NotFoundException("STAFF_NOT_FOUND", "This isn't a staff account.");
    }
    return user;
  }

  /** An admin can't lock themselves out by mistake; another admin has to do it. */
  private static void notYourself(UUID adminId, UUID userId, String field) {
    if (adminId.equals(userId)) {
      throw new FieldValidationException(
          field, "CANNOT_CHANGE_SELF", "You can't change your own account here.");
    }
  }

  /** Someone must always be able to run the library's settings and staff. */
  private void notLastAdmin(User user, String field) {
    if (user.getStaffRole() == Role.ADMIN
        && user.getStatus() == UserStatus.ACTIVE
        && users.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
      throw new FieldValidationException(
          field, "LAST_ADMIN", "This is the only active admin. Make someone else an admin first.");
    }
  }

  private void endSessions(User user) {
    int ended = refreshTokens.revokeAllForUser(user.getId(), Instant.now(clock));
    log.debug("Ended {} session(s) of {}", ended, user.getId());
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

  /**
   * Creates the very first admin from configuration (see AdminBootstrap). Does nothing once any
   * admin exists, so it can't be used later to add admins or reset a password.
   *
   * @return true if an admin was created
   */
  @Transactional
  public boolean bootstrapAdmin(String email, String password, String fullName) {
    if (users.existsByRolesContaining(Role.ADMIN)) {
      log.debug("An admin already exists: bootstrap admin skipped");
      return false;
    }
    String normalizedEmail = User.normalizeEmail(email);
    if (users.existsByEmail(normalizedEmail)) {
      // Silently turning an existing (e.g. member) account into an admin would be a surprise
      log.warn(
          "Bootstrap admin skipped: {} already has a non-admin account. Invite an admin instead.",
          normalizedEmail);
      return false;
    }
    // A weak password stops startup with a clear message instead of creating a guessable admin
    passwordPolicy.check(password, "BOOTSTRAP_ADMIN_PASSWORD", normalizedEmail);

    User admin =
        User.inviteStaff(
            normalizedEmail,
            passwordEncoder.encode(password),
            fullName == null || fullName.isBlank() ? "Administrator" : fullName,
            null,
            Language.EN,
            Role.ADMIN);
    admin.verifyEmail(Instant.now(clock)); // set up by the operator, nothing to confirm
    users.save(admin);
    audit.record("auth.admin_bootstrapped", null, "user", admin.getId(), Map.of());
    log.info(
        "Created the first admin {}. Remove BOOTSTRAP_ADMIN_PASSWORD from the environment now.",
        normalizedEmail);
    return true;
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
