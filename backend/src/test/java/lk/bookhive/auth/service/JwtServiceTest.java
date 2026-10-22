package lk.bookhive.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lk.bookhive.auth.config.JwtConfig;
import lk.bookhive.auth.config.JwtProperties;
import lk.bookhive.auth.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
  private static final UUID USER_ID = UUID.fromString("7b0c6a3e-1d2f-4c1a-9a51-2f6c1c0b9e11");

  private final JwtConfig config = new JwtConfig();
  private final JwtProperties properties = properties(null, null, true);
  private final RSAKey key = generatedKey(properties);
  private final JwtService jwtService =
      new JwtService(config.jwtEncoder(key), properties, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void accessTokenHoldsUserIdRolesIssuerAndExpiry() throws Exception {
    JwtService.AccessToken token =
        jwtService.issueAccessToken(USER_ID, List.of(Role.MEMBER, Role.LIBRARIAN));

    Jwt jwt = decoderAt(NOW).decode(token.value());

    assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
    assertThat(jwt.getClaimAsStringList(JwtService.ROLES_CLAIM))
        .containsExactly("LIBRARIAN", "MEMBER");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("bookhive");
    assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());
    assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", "test-key");
    // Signed, not encrypted: anyone can read it, so no personal data inside
    assertThat(jwt.getClaims()).doesNotContainKeys("email", "name");
  }

  @Test
  void expiredTokenIsRejected() throws Exception {
    String token = jwtService.issueAccessToken(USER_ID, List.of(Role.MEMBER)).value();

    // 16 minutes later (the validator allows 60 s of clock skew)
    JwtDecoder later = decoderAt(NOW.plus(Duration.ofMinutes(17)));

    assertThatThrownBy(() -> later.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void tamperedTokenIsRejected() throws Exception {
    String token = jwtService.issueAccessToken(USER_ID, List.of(Role.MEMBER)).value();
    String[] parts = token.split("\\.");
    // Swap in a payload claiming ADMIN; the signature no longer matches
    String fakePayload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                ("{\"sub\":\"" + USER_ID + "\",\"roles\":[\"ADMIN\"],\"iss\":\"bookhive\"}")
                    .getBytes());
    String forged = parts[0] + "." + fakePayload + "." + parts[2];

    assertThatThrownBy(() -> decoderAt(NOW).decode(forged)).isInstanceOf(JwtException.class);
  }

  @Test
  void tokenSignedWithAnotherKeyIsRejected() throws Exception {
    JwtService otherServer =
        new JwtService(
            config.jwtEncoder(generatedKey(properties)),
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC));
    String token = otherServer.issueAccessToken(USER_ID, List.of(Role.ADMIN)).value();

    assertThatThrownBy(() -> decoderAt(NOW).decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void loadsConfiguredPemKeysWithEscapedNewlines() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
    String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());

    RSAKey loaded = config.jwtSigningKey(properties(privatePem, publicPem, false));

    assertThat(loaded.toRSAPublicKey()).isEqualTo(pair.getPublic());
    assertThat(loaded.getKeyID()).isEqualTo("test-key");
  }

  @Test
  void refusesToStartWithoutKeysOutsideDevAndTest() {
    assertThatThrownBy(() -> config.jwtSigningKey(properties(null, null, false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_PRIVATE_KEY");
  }

  private JwtDecoder decoderAt(Instant now) throws Exception {
    return config.jwtDecoder(key, properties, Clock.fixed(now, ZoneOffset.UTC));
  }

  private RSAKey generatedKey(JwtProperties props) {
    try {
      return config.jwtSigningKey(props);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static JwtProperties properties(String privateKey, String publicKey, boolean generate) {
    return new JwtProperties(
        "bookhive",
        Duration.ofMinutes(15),
        Duration.ofDays(7),
        privateKey,
        publicKey,
        "test-key",
        generate);
  }

  // PEM as it would be pasted into an env var: line breaks written as the two characters \n
  private static String pem(String type, byte[] der) {
    String base64 = Base64.getEncoder().encodeToString(der);
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < base64.length(); i += 64) {
      body.append(base64, i, Math.min(i + 64, base64.length())).append("\\n");
    }
    return "-----BEGIN " + type + "-----\\n" + body + "-----END " + type + "-----";
  }
}
