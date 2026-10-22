package lk.bookhive.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.jwt.*} settings (ADR 0001).
 *
 * @param issuer value of the {@code iss} claim; tokens from anyone else are rejected
 * @param accessTokenTtl how long an access token is valid (15 minutes)
 * @param refreshTokenTtl how long a refresh token is valid (7 days)
 * @param privateKey RSA private key, PKCS#8 PEM ("-----BEGIN PRIVATE KEY-----"); secret
 * @param publicKey matching RSA public key, X.509 PEM ("-----BEGIN PUBLIC KEY-----")
 * @param keyId {@code kid} header, so keys can be rotated later
 * @param allowGeneratedKeys dev/test only: create a throwaway key pair when none is configured
 */
@ConfigurationProperties("bookhive.jwt")
public record JwtProperties(
    String issuer,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    String privateKey,
    String publicKey,
    String keyId,
    boolean allowGeneratedKeys) {

  boolean hasKeys() {
    return privateKey != null && !privateKey.isBlank() && publicKey != null && !publicKey.isBlank();
  }
}
