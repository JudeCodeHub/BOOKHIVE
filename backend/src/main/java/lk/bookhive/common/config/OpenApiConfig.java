package lk.bookhive.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import org.springdoc.core.customizers.OpenApiCustomizer;
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

  /**
   * Response DTOs always contain every field (Jackson also writes nulls), so mark them all as
   * required. Without this every generated TypeScript field would be optional ({@code id?:}) and
   * the frontend would have to check each one. Fields that can be null say so with
   * {@code @Schema(nullable = true)}. Request DTOs keep springdoc's own rules (@NotBlank etc.).
   */
  @Bean
  public OpenApiCustomizer responseFieldsAreAlwaysPresent() {
    return openApi ->
        openApi
            .getComponents()
            .getSchemas()
            .forEach(
                (name, schema) -> {
                  // Naming convention for response types: ...Response, ...Summary, and the
                  // ...Report / ...Result records some services return as they are
                  boolean isResponse =
                      name.endsWith("Response")
                          || name.endsWith("Summary")
                          || name.endsWith("Report")
                          || name.endsWith("Result");
                  if (isResponse && schema.getProperties() != null) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                  }
                });
  }
}
