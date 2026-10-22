package lk.bookhive.common.config;

import java.time.Duration;
import java.util.List;
import lk.bookhive.common.web.RequestIdFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Lets the Next.js frontend (another origin, e.g. localhost:3000) call the API from the browser.
 * Spring Security picks this bean up with {@code http.cors(withDefaults())} (step 129).
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept-Language", RequestIdFilter.HEADER));
    config.setExposedHeaders(List.of(RequestIdFilter.HEADER));
    // Needed so the browser sends the httpOnly refresh-token cookie
    config.setAllowCredentials(true);
    // Browser may cache the preflight (OPTIONS) answer for an hour
    config.setMaxAge(Duration.ofHours(1));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
