package lk.bookhive.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.auth.domain.VerificationToken;
import lk.bookhive.auth.domain.VerificationTokenType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

  /** The token behind an email link, with its user loaded (we always need the user next). */
  @EntityGraph(attributePaths = "user")
  Optional<VerificationToken> findByTokenHashAndType(String tokenHash, VerificationTokenType type);

  /**
   * Marks a user's still-unused links of one type as used, e.g. so only the newest "reset your
   * password" email works.
   */
  @Modifying
  @Query(
      """
      update VerificationToken t set t.usedAt = :now, t.updatedAt = :now
      where t.user.id = :userId and t.type = :type and t.usedAt is null
      """)
  int invalidateUnused(
      @Param("userId") UUID userId,
      @Param("type") VerificationTokenType type,
      @Param("now") Instant now);
}
