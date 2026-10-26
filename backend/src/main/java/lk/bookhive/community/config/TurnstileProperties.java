package lk.bookhive.community.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.turnstile.*}: Cloudflare Turnstile, the "I'm not a robot" check on public forms.
 * Dev uses Cloudflare's test secret, which accepts every token.
 *
 * @param enabled false in automated tests only
 */
@ConfigurationProperties("bookhive.turnstile")
public record TurnstileProperties(
    boolean enabled, String secret, String verifyUrl, Duration timeout) {}
