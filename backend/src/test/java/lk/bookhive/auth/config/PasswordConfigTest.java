package lk.bookhive.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigTest {

  private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

  @Test
  void hashesWithBcryptStrength12AndAlgorithmPrefix() {
    String hash = encoder.encode("correct horse battery staple");

    assertThat(hash).startsWith("{bcrypt}$2a$12$");
    // Fits the users.password_hash column
    assertThat(hash.length()).isLessThanOrEqualTo(100);
  }

  @Test
  void verifiesOnlyTheRightPassword() {
    String hash = encoder.encode("correct horse battery staple");

    assertThat(encoder.matches("correct horse battery staple", hash)).isTrue();
    assertThat(encoder.matches("Correct horse battery staple", hash)).isFalse();
  }

  @Test
  void samePasswordGivesDifferentHashes() {
    // Each hash has its own random salt, so two users with the same password look different
    assertThat(encoder.encode("password123")).isNotEqualTo(encoder.encode("password123"));
  }
}
