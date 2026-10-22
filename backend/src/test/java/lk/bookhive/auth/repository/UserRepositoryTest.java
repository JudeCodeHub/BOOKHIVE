package lk.bookhive.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lk.bookhive.IntegrationTest;
import lk.bookhive.auth.domain.Role;
import lk.bookhive.auth.domain.User;
import lk.bookhive.common.domain.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class UserRepositoryTest {

  @Autowired private UserRepository users;

  @Test
  void findsUserByNormalizedEmail() {
    users.save(User.register("Nimali@Mail.lk", "hash", "Nimali Perera", null, Language.SI));

    assertThat(users.findByEmail(User.normalizeEmail(" NIMALI@mail.LK ")))
        .hasValueSatisfying(
            user -> {
              assertThat(user.getFullName()).isEqualTo("Nimali Perera");
              assertThat(user.getRoles()).containsExactly(Role.MEMBER);
            });
    assertThat(users.findByEmail("someone-else@mail.lk")).isEmpty();
  }

  @Test
  void checksWhetherEmailIsTaken() {
    users.save(User.register("arjun@mail.lk", "hash", "Arjun", null, Language.TA));

    assertThat(users.existsByEmail("arjun@mail.lk")).isTrue();
    assertThat(users.existsByEmail("nobody@mail.lk")).isFalse();
  }
}
