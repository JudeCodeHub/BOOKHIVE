package lk.bookhive.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API docs. Swagger UI: http://localhost:8080/swagger-ui.html · JSON spec: /v3/api-docs (the
 * frontend generates its TypeScript types from it). Disabled in prod.
 */
@Configuration
public class OpenApiConfig {

  /** Name used by {@code @SecurityRequirement(name = "bearer")} on protected controllers. */
  public static final String BEARER = "bearer";

  @Bean
  public OpenAPI bookHiveOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("BookHive API")
                .version("v1")
                .description("Public library platform: catalog, members, loans, holds, fines."))
        // Adds the "Authorize" button in Swagger UI: paste an accessToken from /auth/login
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
