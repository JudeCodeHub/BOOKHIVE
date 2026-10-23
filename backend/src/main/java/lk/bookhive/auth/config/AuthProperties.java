package lk.bookhive.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.auth.*} settings.
 *
 * @param emailVerificationTtl how long a "verify your email" link works
 * @param passwordResetTtl how long a "reset your password" link works (short: it's powerful)
 * @param staffInviteTtl how long a staff invite link works
 * @param refreshCookieSecure send the refresh cookie over HTTPS only; false only for local http
 * @param maxFailedLogins wrong passwords in a row before the account is locked (AUTH-6)
 * @param lockoutDuration how long the account stays locked
 */
@ConfigurationProperties("bookhive.auth")
public record AuthProperties(
    Duration emailVerificationTtl,
    Duration passwordResetTtl,
    Duration staffInviteTtl,
    boolean refreshCookieSecure,
    int maxFailedLogins,
    Duration lockoutDuration) {}
