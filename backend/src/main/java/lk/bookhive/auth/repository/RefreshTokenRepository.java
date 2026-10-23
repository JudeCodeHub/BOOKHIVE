package lk.bookhive.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /** The token from a cookie, with its user loaded (needed for the new access token). */
  @EntityGraph(attributePaths = "user")
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Revokes every still-active token of one login session (reuse detection, logout). One UPDATE
   * statement instead of loading each row.
   */
  @Modifying
  @Query(
      """
      update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now
      where t.familyId = :familyId and t.revokedAt is null
      """)
  int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

  /** Revokes all sessions of a user (log out everywhere, password reset). */
  @Modifying
  @Query(
      """
      update RefreshToken t set t.revokedAt = :now, t.updatedAt = :now
      where t.user.id = :userId and t.revokedAt is null
      """)
  int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
