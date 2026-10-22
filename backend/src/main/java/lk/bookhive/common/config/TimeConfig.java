package lk.bookhive.common.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

/**
 * The single source of "now". Code asks the {@link Clock} instead of calling {@code Instant.now()},
 * so tests can freeze time (e.g. "it's 3 days after the due date").
 */
@Configuration
public class TimeConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  // createdAt / updatedAt also come from the Clock
  @Bean
  public DateTimeProvider auditingDateTimeProvider(Clock clock) {
    return () -> Optional.of(Instant.now(clock));
  }
}
