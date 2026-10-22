package lk.bookhive.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lk.bookhive.common.domain.Language;
import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void registerCreatesUnverifiedMember() {
    User user = User.register("nimali@mail.lk", "hash", "Nimali Perera", null, Language.SI);

    assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
    assertThat(user.getRoles()).containsExactly(Role.MEMBER);
    assertThat(user.hasRole(Role.ADMIN)).isFalse();
    assertThat(user.getPreferredLanguage()).isEqualTo(Language.SI);
    assertThat(user.getEmailVerifiedAt()).isNull();
    assertThat(user.getFailedLogins()).isZero();
  }

  @Test
  void registerNormalisesInput() {
    User user = User.register("  Nimali@Mail.LK ", "hash", "  Nimali Perera ", "  ", Language.EN);

    assertThat(user.getEmail()).isEqualTo("nimali@mail.lk");
    assertThat(user.getFullName()).isEqualTo("Nimali Perera");
    assertThat(user.getPhone()).isNull();
  }

  @Test
  void rolesCannotBeChangedFromOutside() {
    User user = User.register("a@b.lk", "hash", "A", null, Language.EN);

    assertThatThrownBy(() -> user.getRoles().add(Role.ADMIN))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void languageCodesMatchTheFrontendUrls() {
    assertThat(Language.fromCode("si")).isEqualTo(Language.SI);
    assertThat(Language.TA.code()).isEqualTo("ta");
    assertThatThrownBy(() -> Language.fromCode("fr")).isInstanceOf(IllegalArgumentException.class);
  }
}
