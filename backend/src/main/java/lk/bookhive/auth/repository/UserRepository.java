package lk.bookhive.auth.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.auth.domain.Role;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /** Is there at least one account with this role? (e.g. does an admin exist yet) */
  boolean existsByRolesContaining(Role role);

  /** Staff accounts (step 358): everyone with any of these roles, by name. */
  @Query("select distinct u from User u join u.roles r where r in :roles order by u.fullName, u.id")
  List<User> findByAnyRole(@Param("roles") Collection<Role> roles);

  /** How many accounts with this role are in this state (e.g. active admins). */
  @Query(
      "select count(distinct u) from User u join u.roles r where r = :role and u.status = :status")
  long countByRoleAndStatus(@Param("role") Role role, @Param("status") UserStatus status);
}
