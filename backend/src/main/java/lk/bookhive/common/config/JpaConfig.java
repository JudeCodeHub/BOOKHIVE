package lk.bookhive.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on JPA auditing so {@code @CreatedDate} and {@code @LastModifiedDate} are filled in, using
 * the time from {@link TimeConfig}.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {}
