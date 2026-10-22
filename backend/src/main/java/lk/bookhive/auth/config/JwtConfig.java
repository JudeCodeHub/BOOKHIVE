package lk.bookhive.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signing (JwtEncoder) and checking (JwtDecoder) of access tokens with one RSA key pair (RS256).
 * Only the server has the private key, so only the server can create valid tokens.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class})
public class JwtConfig {

  private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

  @Bean
  public RSAKey jwtSigningKey(JwtProperties properties) throws JOSEException {
    if (properties.hasKeys()) {
      return new RSAKey.Builder(parsePublicKey(properties.publicKey()))
          .privateKey(parsePrivateKey(properties.privateKey()))
          .keyID(properties.keyId())
          .build();
    }
    if (!properties.allowGeneratedKeys()) {
      throw new IllegalStateException(
          "JWT_PRIVATE_KEY and JWT_PUBLIC_KEY must be set (see backend/.env.example)");
    }
    // Dev/test convenience: a new key pair on every start, so access tokens from before a
    // restart stop working (the frontend just refreshes). Never used in prod.
    log.warn("No JWT keys configured: using a temporary generated key pair (dev/test only)");
    return new RSAKeyGenerator(2048).keyID(properties.keyId()).generate();
  }

  @Bean
  public JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
  }

  /** Accepts a token only if the signature, issuer and expiry are all valid. */
  @Bean
  public JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties, Clock clock)
      throws JOSEException {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
    JwtTimestampValidator timestamps = new JwtTimestampValidator();
    timestamps.setClock(clock); // same "now" as the rest of the app, so tests can control it
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            timestamps, new JwtIssuerValidator(properties.issuer())));
    return decoder;
  }

  // Env vars can't easily hold line breaks, so "\n" written as two characters is accepted too
  private static RSAPrivateKey parsePrivateKey(String pem) {
    return RsaKeyConverters.pkcs8().convert(toStream(pem));
  }

  private static RSAPublicKey parsePublicKey(String pem) {
    return RsaKeyConverters.x509().convert(toStream(pem));
  }

  private static ByteArrayInputStream toStream(String pem) {
    return new ByteArrayInputStream(pem.replace("\\n", "\n").getBytes(StandardCharsets.UTF_8));
  }
}
