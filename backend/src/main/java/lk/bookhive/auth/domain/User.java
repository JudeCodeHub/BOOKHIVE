package lk.bookhive.auth.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.domain.Language;

/**
 * A person who can log in: member, librarian or admin (table {@code users}). Holds identity and
 * login state only; library membership details belong to the member module.
 *
 * <p>Fields change only through methods named after what happens ({@link #register}), not setters,
 * so the account can't end up in an invalid state.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

  // citext in the database (case-insensitive unique); always stored lowercase as well, so
  // queries match no matter how the JDBC driver types the parameter
  @Column(nullable = false, columnDefinition = "citext")
  private String email;

  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 150)
  private String fullName;

  @Column(length = 20)
  private String phone;

  @Column(nullable = false, length = 2)
  private Language preferredLanguage;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private UserStatus status;

  private Instant emailVerifiedAt;

  @Column(nullable = false)
  private int failedLogins;

  private Instant lockedUntil;

  // Loaded with the user: every request that checks permissions needs them, and there are few
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private Set<Role> roles = new HashSet<>();

  /** For JPA only. */
  protected User() {}

  /**
   * A new self-registered account (AUTH-1): a MEMBER who must verify their email before logging in.
   * The password must already be hashed.
   */
  public static User register(
      String email, String passwordHash, String fullName, String phone, Language language) {
    User user = new User();
    user.email = normalizeEmail(email);
    user.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    user.fullName = Objects.requireNonNull(fullName, "fullName").strip();
    user.phone = phone == null || phone.isBlank() ? null : phone.strip();
    user.preferredLanguage = Objects.requireNonNull(language, "language");
    user.status = UserStatus.PENDING_VERIFICATION;
    user.roles.add(Role.MEMBER);
    return user;
  }

  /**
   * A staff account created by an admin (staff don't self-register). It can't log in until the
   * person accepts the emailed invite and chooses a password; until then {@code passwordHash} is a
   * hash of a random value nobody knows.
   *
   * @param role LIBRARIAN or ADMIN (an admin is also a librarian through the role hierarchy)
   */
  public static User inviteStaff(
      String email,
      String unusablePasswordHash,
      String fullName,
      String phone,
      Language language,
      Role role) {
    if (role != Role.LIBRARIAN && role != Role.ADMIN) {
      throw new IllegalArgumentException("Staff role must be LIBRARIAN or ADMIN, not " + role);
    }
    User user = register(email, unusablePasswordHash, fullName, phone, language);
    user.roles.clear();
    user.roles.add(role);
    return user;
  }

  /** Librarian <-> admin (step 358). Any other role the person has (e.g. MEMBER) stays. */
  public void changeStaffRole(Role role) {
    if (role != Role.LIBRARIAN && role != Role.ADMIN) {
      throw new IllegalArgumentException("Staff role must be LIBRARIAN or ADMIN, not " + role);
    }
    roles.remove(Role.LIBRARIAN);
    roles.remove(Role.ADMIN);
    roles.add(role);
  }

  /** Switched off by an admin (e.g. left the library): can't log in any more. */
  public void disable() {
    status = UserStatus.DISABLED;
  }

  /** Switched back on: active again, or still waiting for the invite to be accepted. */
  public void enable() {
    if (status == UserStatus.DISABLED) {
      status = emailVerifiedAt != null ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION;
    }
  }

  /** The staff role: ADMIN or LIBRARIAN, or null for someone who isn't staff. */
  public Role getStaffRole() {
    return roles.contains(Role.ADMIN)
        ? Role.ADMIN
        : roles.contains(Role.LIBRARIAN) ? Role.LIBRARIAN : null;
  }

  /** " Nimali@Mail.LK " -> "nimali@mail.lk". Use it for every email lookup too. */
  public static String normalizeEmail(String email) {
    return Objects.requireNonNull(email, "email").strip().toLowerCase(Locale.ROOT);
  }

  /**
   * The user clicked the link in the verification email (AUTH-2): the account becomes ACTIVE and
   * can log in. A disabled account stays disabled.
   */
  public void verifyEmail(Instant now) {
    if (emailVerifiedAt == null) {
      emailVerifiedAt = now;
    }
    if (status == UserStatus.PENDING_VERIFICATION) {
      status = UserStatus.ACTIVE;
    }
  }

  /**
   * Edits the profile (AUTH-5). A null argument leaves that field unchanged; a blank phone removes
   * it. The email can't be changed here: it would need verifying again.
   */
  public void updateProfile(String fullName, String phone, Language language) {
    if (fullName != null) {
      if (fullName.isBlank()) {
        throw new IllegalArgumentException("fullName must not be blank");
      }
      this.fullName = fullName.strip();
    }
    if (phone != null) {
      this.phone = phone.isBlank() ? null : phone.strip();
    }
    if (language != null) {
      this.preferredLanguage = language;
    }
  }

  /** Locked after too many wrong passwords (AUTH-6), until {@link #getLockedUntil()}. */
  public boolean isLockedAt(Instant now) {
    return lockedUntil != null && now.isBefore(lockedUntil);
  }

  /**
   * A wrong password. After {@code maxAttempts} in a row the account is locked for {@code
   * lockDuration}, and the count starts again.
   */
  public void recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
    failedLogins++;
    if (failedLogins >= maxAttempts) {
      lockedUntil = now.plus(lockDuration);
      failedLogins = 0;
    }
  }

  /** A correct password: forget earlier mistakes. */
  public void recordSuccessfulLogin() {
    failedLogins = 0;
    lockedUntil = null;
  }

  /** Stores a new password hash (reset or change). The password must already be hashed. */
  public void changePassword(String newPasswordHash) {
    this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
  }

  public boolean hasRole(Role role) {
    return roles.contains(role);
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getFullName() {
    return fullName;
  }

  public String getPhone() {
    return phone;
  }

  public Language getPreferredLanguage() {
    return preferredLanguage;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  public int getFailedLogins() {
    return failedLogins;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  /** Read-only view; roles change only through methods on this class. */
  public Set<Role> getRoles() {
    return Collections.unmodifiableSet(roles);
  }
}
