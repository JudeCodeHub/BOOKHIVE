package lk.bookhive.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lk.bookhive.common.domain.BaseEntity;

/** A one-time token from an email link (verify email / reset password). Stores only its hash. */
@Entity
@Table(name = "verification_tokens")
public class VerificationToken extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 10)
  private VerificationTokenType type;

  @Column(nullable = false, updatable = false, length = 64)
  private String tokenHash;

  @Column(nullable = false, updatable = false)
  private Instant expiresAt;

  private Instant usedAt;

  /** For JPA only. */
  protected VerificationToken() {}

  public static VerificationToken issue(
      User user, VerificationTokenType type, String tokenHash, Instant expiresAt) {
    VerificationToken token = new VerificationToken();
    token.user = Objects.requireNonNull(user, "user");
    token.type = Objects.requireNonNull(type, "type");
    token.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
    token.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    return token;
  }

  public boolean isUsed() {
    return usedAt != null;
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  /** Marks the link as spent, so the same email can't be used twice. */
  public void markUsed(Instant now) {
    if (usedAt == null) {
      usedAt = now;
    }
  }

  public User getUser() {
    return user;
  }

  public VerificationTokenType getType() {
    return type;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }
}
