package lk.bookhive.auth.repository;

import java.util.Optional;
import java.util.UUID;
import lk.bookhive.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for {@link User}. Spring Data writes the SQL from the method names. Only the auth
 * module may use it (enforced by ArchitectureTest); other modules go through auth's services.
 *
 * <p>Emails are stored lowercase: pass them through {@link User#normalizeEmail} first.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

  /** Login, forgot password: the account for this (normalized) email, if any. */
  Optional<User> findByEmail(String email);

  /** Registration: is this (normalized) email already taken? */
  boolean existsByEmail(String email);
}
