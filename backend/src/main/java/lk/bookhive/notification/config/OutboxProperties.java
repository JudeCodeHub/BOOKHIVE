package lk.bookhive.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.outbox.*}: how the dispatcher sends queued email and SMS (checklist step 308).
 *
 * @param pollInterval how often it looks for messages to send
 * @param batchSize messages per round
 * @param maxAttempts tries before a message is marked FAILED for an admin to look at
 * @param firstRetry wait after the first failure; doubles each time (1 min, 2, 4, 8...)
 * @param maxRetry the longest wait between two tries
 */
@ConfigurationProperties("bookhive.outbox")
public record OutboxProperties(
    Duration pollInterval, int batchSize, int maxAttempts, Duration firstRetry, Duration maxRetry) {

  /** Wait before the next try, after {@code attemptsSoFar} failed tries (exponential backoff). */
  public Duration retryAfter(int attemptsSoFar) {
    // 1, 2, 4, 8... times the first wait; shift capped so it can't overflow
    long factor = 1L << Math.min(Math.max(attemptsSoFar - 1, 0), 20);
    Duration wait = firstRetry.multipliedBy(factor);
    return wait.compareTo(maxRetry) > 0 ? maxRetry : wait;
  }
}
