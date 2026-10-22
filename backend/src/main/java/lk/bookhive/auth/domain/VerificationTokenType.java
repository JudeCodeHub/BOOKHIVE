package lk.bookhive.auth.domain;

/** What a one-time email link is for. Stored by name in {@code verification_tokens.type}. */
public enum VerificationTokenType {
  /** "Verify your email address" after registering (AUTH-2). */
  EMAIL,
  /** "Reset your password" (AUTH-4). */
  RESET
}
