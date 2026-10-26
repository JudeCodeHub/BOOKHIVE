package lk.bookhive.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code bookhive.library.*}: the library's contact details and location, shown on the visit page
 * (PRD §10). From configuration for now; an admin "library info" page can replace it later.
 *
 * @param latitude for the map; together with longitude
 */
@ConfigurationProperties("bookhive.library")
public record LibraryProperties(
    String name,
    String addressLine,
    String city,
    String postalCode,
    String phone,
    String email,
    double latitude,
    double longitude) {}
