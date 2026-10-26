package lk.bookhive.catalog.api;

import java.time.Instant;
import java.util.UUID;
import lk.bookhive.catalog.domain.PurchaseSuggestion;
import lk.bookhive.catalog.domain.SuggestionStatus;

/** Purchase suggestion JSON shared by the member and staff endpoints. */
final class SuggestionResponses {

  private SuggestionResponses() {}

  /**
   * @param bookId set once acquired: the member can open the book
   */
  public record SuggestionResponse(
      UUID id,
      String title,
      String author,
      String isbn13,
      String reason,
      SuggestionStatus status,
      String staffNote,
      UUID bookId,
      Instant createdAt,
      Instant decidedAt) {

    static SuggestionResponse from(PurchaseSuggestion s) {
      return new SuggestionResponse(
          s.getId(),
          s.getTitle(),
          s.getAuthor(),
          s.getIsbn13(),
          s.getReason(),
          s.getStatus(),
          s.getStaffNote(),
          s.getBook() == null ? null : s.getBook().getId(),
          s.getCreatedAt(),
          s.getDecidedAt());
    }
  }
}
