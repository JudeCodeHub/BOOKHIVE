package lk.bookhive.common.ratelimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.rate-limit.*}: how many requests one client (IP address) may make.
 *
 * <pre>
 * bookhive.rate-limit.rules.login:
 *   path: /api/v1/auth/login
 *   capacity: 10      # requests...
 *   period: 1m        # ...per minute
 * </pre>
 *
 * @param enabled turn all limits off (e.g. load tests)
 * @param rules rule name -> limit; only POST requests to the exact path are counted
 */
@ConfigurationProperties("bookhive.rate-limit")
public record RateLimitProperties(boolean enabled, Map<String, Rule> rules) {

  public RateLimitProperties {
    rules = rules == null ? Map.of() : Map.copyOf(rules);
  }

  public record Rule(String path, long capacity, Duration period) {}
}
