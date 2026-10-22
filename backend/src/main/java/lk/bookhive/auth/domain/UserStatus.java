package lk.bookhive.auth.domain;

/** Account state. Stored by name in {@code users.status}. */
public enum UserStatus {
  /** Registered, but hasn't clicked the email verification link yet; can't log in. */
  PENDING_VERIFICATION,
  /** Can log in. */
  ACTIVE,
  /** Switched off by an admin (e.g. a staff member who left); can't log in. */
  DISABLED
}
