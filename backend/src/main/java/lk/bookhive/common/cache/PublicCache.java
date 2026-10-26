package lk.bookhive.common.cache;

import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * A small Redis cache for public, read-heavy data (checklist step 195): the category tree, book
 * details, home page lists. Stored as JSON under {@code public:...} keys.
 *
 * <ul>
 *   <li>Fail-open: if Redis is down, values are just computed every time.
 *   <li>Changes evict the affected keys <b>after the transaction commits</b>; evicting earlier
 *       would let a reader put the old data straight back into the cache.
 *   <li>Live data (search results, availability) is deliberately not cached.
 * </ul>
 */
@Component
public class PublicCache {

  private static final Logger log = LoggerFactory.getLogger(PublicCache.class);

  private final StringRedisTemplate redis;
  private final JsonMapper json;

  public PublicCache(StringRedisTemplate redis, JsonMapper json) {
    this.redis = redis;
    this.json = json;
  }

  /** The cached value, or {@code load}'s result (then cached for {@code ttl}). */
  public <T> T get(String key, Duration ttl, TypeReference<T> type, Supplier<T> load) {
    try {
      String cached = redis.opsForValue().get(key);
      if (cached != null) {
        return json.readValue(cached, type);
      }
    } catch (RuntimeException e) {
      log.debug("Cache read skipped for {}: {}", key, e.getMessage());
    }
    T value = load.get();
    try {
      redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
    } catch (RuntimeException e) {
      log.debug("Cache write skipped for {}: {}", key, e.getMessage());
    }
    return value;
  }

  /** Removes these keys once the current transaction has committed (now, if there is none). */
  public void evict(String... keys) {
    afterCommit(() -> redis.delete(java.util.List.of(keys)));
  }

  /** Removes every key starting with {@code prefix}, e.g. all "public:new-arrivals:..." sizes. */
  public void evictPrefix(String prefix) {
    afterCommit(
        () -> {
          var keys = redis.keys(prefix + "*");
          if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
          }
        });
  }

  private void afterCommit(Runnable action) {
    Runnable safe =
        () -> {
          try {
            action.run();
          } catch (RuntimeException e) {
            // Entries still expire on their own (short TTLs), so this is never permanent
            log.warn("Cache eviction failed: {}", e.getMessage());
          }
        };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              safe.run();
            }
          });
    } else {
      safe.run();
    }
  }
}
