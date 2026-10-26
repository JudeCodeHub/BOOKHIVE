package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lk.bookhive.catalog.api.SuggestionResponses.SuggestionResponse;
import lk.bookhive.catalog.domain.SuggestionStatus;
import lk.bookhive.catalog.service.PurchaseSuggestionService;
import lk.bookhive.catalog.service.PurchaseSuggestionService.StaffView;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Answering members' purchase suggestions (librarians and admins). */
@RestController
@RequestMapping("/staff/suggestions")
@Tag(name = "Staff: purchase suggestions")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffSuggestionController {

  private final PurchaseSuggestionService suggestions;

  public StaffSuggestionController(PurchaseSuggestionService suggestions) {
    this.suggestions = suggestions;
  }

  /**
   * @param sameIsbnCount how many members asked for this ISBN (1 if no ISBN given)
   */
  public record StaffSuggestionSummary(
      SuggestionResponse suggestion,
      UUID suggestedById,
      String suggestedByName,
      String suggestedByEmail,
      long sameIsbnCount) {

    static StaffSuggestionSummary from(StaffView view) {
      return new StaffSuggestionSummary(
          SuggestionResponse.from(view.suggestion()),
          view.suggestion().getUserId(),
          view.suggestedBy() == null ? null : view.suggestedBy().getFullName(),
          view.suggestedBy() == null ? null : view.suggestedBy().getEmail(),
          view.sameIsbnCount());
    }
  }

  /**
   * @param staffNote shown to the member
   * @param bookId required when status is ACQUIRED
   */
  public record DecisionRequest(
      @NotNull SuggestionStatus status, @Size(max = 1000) String staffNote, UUID bookId) {}

  @GetMapping
  @Operation(summary = "Suggestions by status (default: new ones), oldest first")
  public PageResponse<StaffSuggestionSummary> list(
      @RequestParam(defaultValue = "SUBMITTED") SuggestionStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(suggestions.list(status, page, size), StaffSuggestionSummary::from);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Mark ordered, acquired (with the book) or declined, with a note")
  public SuggestionResponse decide(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody DecisionRequest request) {
    return SuggestionResponse.from(
        suggestions.decide(
            id,
            request.status(),
            request.staffNote(),
            request.bookId(),
            UUID.fromString(jwt.getSubject())));
  }
}
