package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.domain.Isbn;
import lk.bookhive.catalog.repository.BookRepository;
import lk.bookhive.catalog.service.IsbnLookupService;
import lk.bookhive.catalog.service.IsbnLookupService.IsbnLookupResult;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.exception.FieldValidationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Lookup ISBN" while cataloguing (librarians and admins). */
@RestController
@RequestMapping("/staff/isbn")
@Tag(name = "Staff: books")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffIsbnController {

  private final IsbnLookupService lookup;
  private final BookRepository books;

  public StaffIsbnController(IsbnLookupService lookup, BookRepository books) {
    this.lookup = lookup;
    this.books = books;
  }

  /**
   * @param existingBookId set when our catalog already has this ISBN: add a copy to that book
   *     instead of creating a new one
   * @param found whether Open Library knows the ISBN (the other fields are empty if not)
   */
  public record IsbnLookupResponse(
      String isbn13,
      String isbn10,
      UUID existingBookId,
      String existingBookTitle,
      boolean found,
      String title,
      String subtitle,
      List<String> authors,
      String publisher,
      Short publishYear,
      Integer pages,
      String coverImageUrl) {}

  @GetMapping("/{isbn}")
  @Operation(
      summary = "Look up an ISBN (ours first, then Open Library; 503 if Open Library is down)")
  public IsbnLookupResponse lookup(@PathVariable String isbn) {
    Isbn parsed =
        Isbn.parse(isbn)
            .orElseThrow(
                () ->
                    new FieldValidationException(
                        "isbn", "ISBN_INVALID", "This isn't a valid ISBN. Check the digits."));
    Book existing = books.findByIsbn13(parsed.isbn13()).orElse(null);
    IsbnLookupResult result = lookup.lookup(parsed);
    return new IsbnLookupResponse(
        parsed.isbn13(),
        parsed.isbn10(),
        existing == null ? null : existing.getId(),
        existing == null ? null : existing.getTitle(),
        result.found(),
        result.title(),
        result.subtitle(),
        result.authors(),
        result.publisher(),
        result.publishYear(),
        result.pages(),
        result.coverImageUrl());
  }
}
