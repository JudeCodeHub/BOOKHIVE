package lk.bookhive.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import lk.bookhive.IntegrationTest;
import lk.bookhive.common.domain.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the User mapping against the real V2 migration in PostgreSQL. Each test runs in a
 * transaction that is rolled back, so tests don't see each other's rows.
 */
@IntegrationTest
@Transactional
class UserPersistenceTest {

  @Autowired private EntityManager em;

  @Test
  void savesAndLoadsUserWithRoles() {
    User user =
        User.register("Kumari@Library.lk", "hash", "Kumari Silva", "0771234567", Language.TA);
    em.persist(user);
    em.flush();
    em.clear(); // forget the cached object, so the next find really reads the database

    User loaded = em.find(User.class, user.getId());

    assertThat(loaded.getEmail()).isEqualTo("kumari@library.lk");
    assertThat(loaded.getPreferredLanguage()).isEqualTo(Language.TA);
    assertThat(loaded.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    assertThat(loaded.getRoles()).containsExactly(Role.MEMBER);
    assertThat(loaded.getCreatedAt()).isNotNull();
    assertThat(loaded.getUpdatedAt()).isNotNull();
    assertThat(loaded.getVersion()).isZero();
  }

  @Test
  void databaseTreatsEmailsCaseInsensitively() {
    insertUser("nimali@mail.lk", "en", "ACTIVE");

    // Written with raw SQL on purpose, to prove the citext column itself blocks it
    assertThatThrownBy(() -> insertUser("NIMALI@MAIL.LK", "en", "ACTIVE"))
        .isInstanceOf(PersistenceException.class)
        .hasStackTraceContaining("uk_users_email");
  }

  @Test
  void databaseRejectsUnknownLanguage() {
    assertThatThrownBy(() -> insertUser("a@b.lk", "fr", "ACTIVE"))
        .isInstanceOf(PersistenceException.class)
        .hasStackTraceContaining("ck_users_language");
  }

  @Test
  void databaseRejectsUnknownStatus() {
    assertThatThrownBy(() -> insertUser("a@b.lk", "en", "BANNED"))
        .isInstanceOf(PersistenceException.class)
        .hasStackTraceContaining("ck_users_status");
  }

  private void insertUser(String email, String language, String status) {
    em.createNativeQuery(
            """
            INSERT INTO users (email, password_hash, full_name, preferred_language, status)
            VALUES (:email, 'hash', 'Test User', :language, :status)
            """)
        .setParameter("email", email)
        .setParameter("language", language)
        .setParameter("status", status)
        .executeUpdate();
  }
}
