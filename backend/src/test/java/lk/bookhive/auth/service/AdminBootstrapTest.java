package lk.bookhive.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lk.bookhive.IntegrationTest;
import lk.bookhive.auth.domain.Role;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.domain.UserStatus;
import lk.bookhive.auth.repository.UserRepository;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.exception.FieldValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The first admin from configuration: created once, never again, never by promotion. */
@IntegrationTest
class AdminBootstrapTest {

  private static final String PASSWORD = "honey-hive-test-2026";

  @Autowired private AuthService authService;
  @Autowired private SessionService sessionService;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  @AfterEach
  void emptyDatabase() {
    users.deleteAll();
  }

  @Test
  void createsAnActiveAdminWhoCanLogIn() {
    assertThat(authService.bootstrapAdmin("Head@Library.lk", PASSWORD, "Mr. Silva")).isTrue();

    User admin = users.findByEmail("head@library.lk").orElseThrow();
    assertThat(admin.getRoles()).containsExactly(Role.ADMIN);
    assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(admin.getFullName()).isEqualTo("Mr. Silva");
    assertThat(sessionService.login("head@library.lk", PASSWORD).accessToken().value())
        .isNotBlank();
  }

  @Test
  void doesNothingOnceAnAdminExists() {
    authService.bootstrapAdmin("head@library.lk", PASSWORD, null);

    // Another start with different settings must not add a second admin
    assertThat(authService.bootstrapAdmin("other@library.lk", PASSWORD, null)).isFalse();
    assertThat(users.existsByEmail("other@library.lk")).isFalse();
  }

  @Test
  void doesNotPromoteAnExistingAccount() {
    users.save(
        User.register(
            "nimali@mail.lk",
            passwordEncoder.encode("reading-is-fun"),
            "Nimali",
            null,
            Language.SI));

    assertThat(authService.bootstrapAdmin("nimali@mail.lk", PASSWORD, null)).isFalse();
    assertThat(users.findByEmail("nimali@mail.lk").orElseThrow().getRoles())
        .containsExactly(Role.MEMBER);
  }

  @Test
  void refusesAWeakPassword() {
    assertThatThrownBy(() -> authService.bootstrapAdmin("head@library.lk", "password123", null))
        .isInstanceOf(FieldValidationException.class)
        .hasFieldOrPropertyWithValue("code", "PASSWORD_TOO_COMMON");
    assertThat(users.count()).isZero();
  }
}
