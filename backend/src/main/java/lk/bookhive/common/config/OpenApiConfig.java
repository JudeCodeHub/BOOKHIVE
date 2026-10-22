package lk.bookhive.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API docs. Swagger UI: http://localhost:8080/swagger-ui.html · JSON spec: /v3/api-docs (the
 * frontend generates its TypeScript types from it). Disabled in prod.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI bookHiveOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("BookHive API")
                .version("v1")
                .description("Public library platform: catalog, members, loans, holds, fines."));
  }
}
