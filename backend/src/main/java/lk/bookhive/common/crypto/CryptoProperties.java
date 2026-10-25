package lk.bookhive.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.crypto.*} in application.yml.
 *
 * @param fieldKey 32 random bytes, Base64 (openssl rand -base64 32)
 */
@ConfigurationProperties("bookhive.crypto")
public record CryptoProperties(String fieldKey) {}
