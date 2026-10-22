package lk.bookhive.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.Language;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

  private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

  private final User user = User.register("a@b.lk", "hash", "A", null, Language.EN);

  @Test
  void isActiveUntilItExpires() {
    RefreshToken token = issue(NOW.plus(Duration.ofDays(7)));

    assertThat(token.isActive(NOW)).isTrue();
    assertThat(token.isActive(NOW.plus(Duration.ofDays(7)))).isFalse();
  }

  @Test
  void revokedTokenIsNotActiveAndKeepsFirstRevocationTime() {
    RefreshToken token = issue(NOW.plus(Duration.ofDays(7)));

    token.revoke(NOW);
    token.revoke(NOW.plusSeconds(60));

    assertThat(token.isActive(NOW)).isFalse();
    assertThat(token.getRevokedAt()).isEqualTo(NOW);
  }

  private RefreshToken issue(Instant expiresAt) {
    return RefreshToken.issue(user, "a".repeat(64), UUID.randomUUID(), expiresAt);
  }
}
