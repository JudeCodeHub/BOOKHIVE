package lk.bookhive.catalog.api;

import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.api.CategoryResponses.CategorySummary;
import lk.bookhive.catalog.domain.AuthorRole;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.domain.BookAuthor;
import lk.bookhive.catalog.domain.BookFormat;

/** Book JSON shared by the public and the staff endpoints. */
final class BookResponses {

  private BookResponses() {}

  public record ContributorSummary(UUID authorId, String name, AuthorRole role) {

    static ContributorSummary from(BookAuthor contributor) {
      return new ContributorSummary(
          contributor.getAuthor().getId(),
          contributor.getAuthor().getName(),
          contributor.getRole());
    }
  }

  public record PublisherSummary(UUID id, String name) {}

  /**
   * A title with its people and categories. Availability and the cover link are added by the steps
   * that build them (167, 161).
   */
  public record BookResponse(
      UUID id,
      String title,
      String subtitle,
      String isbn10,
      String isbn13,
      String edition,
      Short publishYear,
      Integer pages,
      String language,
      String description,
      BookFormat format,
      String callNumber,
      PublisherSummary publisher,
      List<ContributorSummary> contributors,
      List<CategorySummary> categories) {

    static BookResponse from(Book book) {
      return new BookResponse(
          book.getId(),
          book.getTitle(),
          book.getSubtitle(),
          book.getIsbn10(),
          book.getIsbn13(),
          book.getEdition(),
          book.getPublishYear(),
          book.getPages(),
          book.getLanguage(),
          book.getDescription(),
          book.getFormat(),
          book.getCallNumber(),
          book.getPublisher() == null
              ? null
              : new PublisherSummary(book.getPublisher().getId(), book.getPublisher().getName()),
          book.getContributors().stream().map(ContributorSummary::from).toList(),
          book.getCategories().stream().map(CategorySummary::from).toList());
    }
  }
}
