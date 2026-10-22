package lk.bookhive.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.auth.*} settings.
 *
 * @param emailVerificationTtl how long a "verify your email" link works
 */
@ConfigurationProperties("bookhive.auth")
public record AuthProperties(Duration emailVerificationTtl) {}
