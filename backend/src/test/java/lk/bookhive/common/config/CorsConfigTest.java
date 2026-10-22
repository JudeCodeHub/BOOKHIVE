package lk.bookhive.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigTest {

  private final UrlBasedCorsConfigurationSource source =
      (UrlBasedCorsConfigurationSource)
          new CorsConfig()
              .corsConfigurationSource(new CorsProperties(List.of("http://localhost:3000")));

  @Test
  void allowsOnlyTheConfiguredFrontendOnApiPaths() {
    CorsConfiguration config = source.getCorsConfiguration(request("/api/v1/books"));

    assertThat(config).isNotNull();
    assertThat(config.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
    assertThat(config.checkOrigin("https://evil.example")).isNull();
    assertThat(config.getAllowCredentials()).isTrue();
  }

  @Test
  void noCorsOutsideTheApi() {
    assertThat(source.getCorsConfiguration(request("/actuator/health"))).isNull();
  }

  private static MockHttpServletRequest request(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setRequestURI(uri);
    return request;
  }
}
