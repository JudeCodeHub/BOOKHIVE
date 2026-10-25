package lk.bookhive.catalog.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.isbn-lookup.*}: the Open Library book lookup (CAT-3).
 *
 * @param baseUrl Open Library's address
 * @param timeout give up after this long; staff can still type the details by hand
 * @param userAgent Open Library asks callers to identify themselves with a contact
 * @param cacheTtl how long a found book is remembered in Redis
 * @param notFoundTtl how long "not in Open Library" is remembered (shorter: it may be added)
 */
@ConfigurationProperties("bookhive.isbn-lookup")
public record IsbnLookupProperties(
    String baseUrl, Duration timeout, String userAgent, Duration cacheTtl, Duration notFoundTtl) {}
