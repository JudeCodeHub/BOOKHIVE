package lk.bookhive.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.auth.*} settings.
 *
 * @param emailVerificationTtl how long a "verify your email" link works
 * @param passwordResetTtl how long a "reset your password" link works (short: it's powerful)
 * @param refreshCookieSecure send the refresh cookie over HTTPS only; false only for local http
 */
@ConfigurationProperties("bookhive.auth")
public record AuthProperties(
    Duration emailVerificationTtl, Duration passwordResetTtl, boolean refreshCookieSecure) {}
