package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.api.SuggestionResponses.SuggestionResponse;
import lk.bookhive.catalog.service.PurchaseSuggestionService;
import lk.bookhive.common.config.OpenApiConfig;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A member's own purchase suggestions (logged in). */
@RestController
@RequestMapping("/me/suggestions")
@Tag(name = "Purchase suggestions")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MySuggestionController {

  private final PurchaseSuggestionService suggestions;

  public MySuggestionController(PurchaseSuggestionService suggestions) {
    this.suggestions = suggestions;
  }

  /**
   * @param isbn optional; helps staff order the right edition
   */
  public record SuggestionRequest(
      @NotBlank @Size(max = 300) String title,
      @Size(max = 200) String author,
      @Size(max = 20) String isbn,
      @Size(max = 1000) String reason) {}

  @GetMapping
  @Operation(summary = "My suggestions and the library's answers, newest first")
  public List<SuggestionResponse> mine(@AuthenticationPrincipal Jwt jwt) {
    return suggestions.mine(userId(jwt)).stream().map(SuggestionResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Suggest a book (max 5 open; 409 if the library already has it)")
  public SuggestionResponse suggest(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SuggestionRequest request) {
    return SuggestionResponse.from(
        suggestions.suggest(
            userId(jwt), request.title(), request.author(), request.isbn(), request.reason()));
  }

  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
