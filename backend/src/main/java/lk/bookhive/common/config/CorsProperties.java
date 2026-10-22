package lk.bookhive.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code bookhive.cors.allowed-origins}: the frontend URLs allowed to call the API. */
@ConfigurationProperties("bookhive.cors")
public record CorsProperties(List<String> allowedOrigins) {

  public CorsProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }
}
