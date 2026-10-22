package lk.bookhive.auth.repository;

import java.util.Optional;
import java.util.UUID;
import lk.bookhive.auth.domain.VerificationToken;
import lk.bookhive.auth.domain.VerificationTokenType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

  /** The token behind an email link, with its user loaded (we always need the user next). */
  @EntityGraph(attributePaths = "user")
  Optional<VerificationToken> findByTokenHashAndType(String tokenHash, VerificationTokenType type);
}
