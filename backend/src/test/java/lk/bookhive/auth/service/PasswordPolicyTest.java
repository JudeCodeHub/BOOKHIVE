package lk.bookhive.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lk.bookhive.common.exception.FieldValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

  private final PasswordPolicy policy = new PasswordPolicy();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "password",
        "PASSWORD",
        "12345678",
        "qwertyuiop",
        "Password123!",
        "monkey2026",
        "Bookhive123",
        "srilanka@2026"
      })
  void rejectsCommonPasswordsIgnoringCase(String password) {
    assertThatThrownBy(() -> policy.check(password, "password", "nimali@mail.lk"))
        .isInstanceOf(FieldValidationException.class)
        .extracting("code", "field")
        .containsExactly("PASSWORD_TOO_COMMON", "password");
  }

  @Test
  void rejectsPasswordsBuiltFromTheEmail() {
    assertThatThrownBy(
            () -> policy.check("Nimali.Perera2026", "newPassword", "nimali.perera@mail.lk"))
        .isInstanceOf(FieldValidationException.class)
        .extracting("code", "field")
        .containsExactly("PASSWORD_CONTAINS_EMAIL", "newPassword");
  }

  @Test
  void rejectsPasswordsOverBcryptsLimit() {
    // 30 Sinhala letters = 90 bytes in UTF-8
    assertThatThrownBy(() -> policy.check("අ".repeat(30), "password", "a@b.lk"))
        .isInstanceOf(FieldValidationException.class)
        .extracting("code")
        .isEqualTo("PASSWORD_TOO_LONG");
  }

  @ParameterizedTest
  @ValueSource(strings = {"reading-is-fun", "මගේ පොත් 2026", "correct horse battery"})
  void acceptsUncommonPasswordsInAnyScript(String password) {
    assertThatCode(() -> policy.check(password, "password", "nimali@mail.lk"))
        .doesNotThrowAnyException();
  }
}
