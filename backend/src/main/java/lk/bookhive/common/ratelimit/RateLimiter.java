package lk.bookhive.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lk.bookhive.common.ratelimit.RateLimitProperties.Rule;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Token buckets in Redis (Bucket4j). Each client gets a bucket per rule holding {@code capacity}
 * tokens, refilled evenly over {@code period}; each request takes one. Because the buckets live in
 * Redis, the limit holds across all backend instances.
 */
@Component
public class RateLimiter {

  private final LettuceConnectionFactory redis;
  private volatile ProxyManager<byte[]> buckets;

  public RateLimiter(LettuceConnectionFactory redis) {
    this.redis = redis;
  }

  /**
   * Takes one token from this client's bucket for the rule.
   *
   * @return how long to wait before retrying, or {@link Duration#ZERO} if the request may proceed
   */
  public Duration tryConsume(String ruleName, Rule rule, String clientKey) {
    byte[] key = ("rate-limit:" + ruleName + ":" + clientKey).getBytes(StandardCharsets.UTF_8);
    ConsumptionProbe probe =
        buckets().builder().build(key, () -> configuration(rule)).tryConsumeAndReturnRemaining(1);
    return probe.isConsumed() ? Duration.ZERO : Duration.ofNanos(probe.getNanosToWaitForRefill());
  }

  private static BucketConfiguration configuration(Rule rule) {
    return BucketConfiguration.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(rule.capacity())
                .refillGreedy(rule.capacity(), rule.period())
                .build())
        .build();
  }

  // Connects to Redis on first use, not at startup: if Redis is down the app still starts
  private ProxyManager<byte[]> buckets() {
    if (buckets == null) {
      synchronized (this) {
        if (buckets == null) {
          RedisClient client = (RedisClient) redis.getNativeClient();
          buckets =
              Bucket4jLettuce.casBasedBuilder(client)
                  // Delete a bucket from Redis once it would be full again anyway
                  .expirationAfterWrite(
                      ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                          Duration.ofSeconds(10)))
                  .build();
        }
      }
    }
    return buckets;
  }
}
