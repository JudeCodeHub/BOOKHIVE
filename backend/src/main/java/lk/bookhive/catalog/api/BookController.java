package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.api.BookResponses.BookResponse;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.domain.BookFormat;
import lk.bookhive.catalog.service.BookListService;
import lk.bookhive.catalog.service.BookListService.BookListItem;
import lk.bookhive.catalog.service.BookListService.BookSort;
import lk.bookhive.catalog.service.BookListService.Filters;
import lk.bookhive.catalog.service.BookService;
import lk.bookhive.common.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public catalog: anyone can look at a book (PRD §10). */
@RestController
@RequestMapping("/books")
@Tag(name = "Catalog")
public class BookController {

  private final BookService books;
  private final BookListService bookList;

  public BookController(BookService books, BookListService bookList) {
    this.books = books;
    this.bookList = bookList;
  }

  /** One book in a list: enough for a result card (cover and availability come later). */
  public record BookSummary(
      UUID id,
      String title,
      String subtitle,
      List<String> contributors,
      Short publishYear,
      String language,
      BookFormat format,
      String callNumber) {

    static BookSummary from(BookListItem item) {
      Book book = item.book();
      return new BookSummary(
          book.getId(),
          book.getTitle(),
          book.getSubtitle(),
          item.contributorNames(),
          book.getPublishYear(),
          book.getLanguage(),
          book.getFormat(),
          book.getCallNumber());
    }
  }

  @GetMapping
  @Operation(summary = "Browse books page by page, with filters and sorting")
  public PageResponse<BookSummary> list(
      @RequestParam(required = false) @Pattern(regexp = "^[a-z]{2,3}$") String language,
      @RequestParam(required = false) BookFormat format,
      @RequestParam(required = false) UUID category,
      @RequestParam(defaultValue = "TITLE") BookSort sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(
        bookList.list(new Filters(language, format, category), sort, page, size),
        BookSummary::from);
  }

  @GetMapping("/{id}")
  @Operation(summary = "One book with its authors, publisher and categories")
  public BookResponse get(@PathVariable UUID id) {
    return BookResponse.from(books.get(id));
  }
}
