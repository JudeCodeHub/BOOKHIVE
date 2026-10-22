package lk.bookhive.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;

/**
 * One issued refresh token (ADR 0001). Stores only the hash of the token the browser holds.
 * Rotation, reuse detection and the cookie are handled by the auth service (checklist step 93).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, updatable = false)
  private User user;

  @Column(nullable = false, updatable = false, length = 64)
  private String tokenHash;

  @Column(nullable = false, updatable = false)
  private UUID familyId;

  @Column(nullable = false, updatable = false)
  private Instant expiresAt;

  private Instant revokedAt;

  /** For JPA only. */
  protected RefreshToken() {}

  /** A token for a new login (starts a new family) or a rotation (keeps the family). */
  public static RefreshToken issue(User user, String tokenHash, UUID familyId, Instant expiresAt) {
    RefreshToken token = new RefreshToken();
    token.user = Objects.requireNonNull(user, "user");
    token.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
    token.familyId = Objects.requireNonNull(familyId, "familyId");
    token.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    return token;
  }

  /** Usable right now: not revoked and not expired. */
  public boolean isActive(Instant now) {
    return revokedAt == null && now.isBefore(expiresAt);
  }

  /** Revoking twice keeps the first time, which is the one that matters for auditing. */
  public void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }

  public User getUser() {
    return user;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }
}
