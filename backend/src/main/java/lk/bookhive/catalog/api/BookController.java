package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lk.bookhive.catalog.api.BookResponses.BookResponse;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.domain.BookFormat;
import lk.bookhive.catalog.service.AvailabilityService;
import lk.bookhive.catalog.service.AvailabilityService.Availability;
import lk.bookhive.catalog.service.BookListService;
import lk.bookhive.catalog.service.BookListService.BookListItem;
import lk.bookhive.catalog.service.BookListService.BookSort;
import lk.bookhive.catalog.service.BookListService.Filters;
import lk.bookhive.catalog.service.BookService;
import lk.bookhive.catalog.service.CoverService;
import lk.bookhive.catalog.service.CoverService.CoverSize;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.web.PageResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
  private final CoverService covers;
  private final AvailabilityService availability;

  public BookController(
      BookService books,
      BookListService bookList,
      CoverService covers,
      AvailabilityService availability) {
    this.books = books;
    this.bookList = bookList;
    this.covers = covers;
    this.availability = availability;
  }

  /** Counts per state; see {@link Availability} for what each number means. */
  public record AvailabilityResponse(
      long total,
      long available,
      long availableToBorrow,
      long referenceOnly,
      long onLoan,
      long onHoldShelf,
      long inTransit,
      long inRepair,
      long holdsWaiting,
      List<String> shelves) {

    static AvailabilityResponse from(Availability a) {
      return new AvailabilityResponse(
          a.total(),
          a.available(),
          a.availableToBorrow(),
          a.referenceOnly(),
          a.onLoan(),
          a.onHoldShelf(),
          a.inTransit(),
          a.inRepair(),
          a.holdsWaiting(),
          a.shelves());
    }
  }

  @GetMapping("/{id}/availability")
  @Operation(summary = "How many copies are on the shelf, on loan, waiting... and where")
  public AvailabilityResponse availability(@PathVariable UUID id) {
    return AvailabilityResponse.from(availability.forBook(id));
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
      String callNumber,
      String coverVersion) {

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
          book.getCallNumber(),
          CoverService.version(book));
    }
  }

  @GetMapping
  @Operation(summary = "Browse books page by page; q = ISBN or part of the title")
  public PageResponse<BookSummary> list(
      @RequestParam(required = false) @Size(max = 200) String q,
      @RequestParam(required = false) @Pattern(regexp = "^[a-z]{2,3}$") String language,
      @RequestParam(required = false) BookFormat format,
      @RequestParam(required = false) UUID category,
      @RequestParam(defaultValue = "TITLE") BookSort sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(
        bookList.list(new Filters(q, language, format, category), sort, page, size),
        BookSummary::from);
  }

  @GetMapping("/{id}")
  @Operation(summary = "One book with its authors, publisher and categories")
  public BookResponse get(@PathVariable UUID id) {
    return BookResponse.from(books.get(id));
  }

  /**
   * The cover picture. Public, and cached by browsers for a year: the URL carries ?v= (the cover
   * version), which changes when staff upload a new cover.
   */
  @GetMapping(path = "/{id}/cover/{size}", produces = MediaType.IMAGE_JPEG_VALUE)
  @Operation(summary = "Cover image: small (128 px), medium (320) or large (800)")
  public ResponseEntity<byte[]> cover(@PathVariable UUID id, @PathVariable String size) {
    CoverSize coverSize;
    try {
      coverSize = CoverSize.valueOf(size.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new NotFoundException("COVER_NOT_FOUND", "Sizes are small, medium and large.");
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
        .contentType(MediaType.IMAGE_JPEG)
        .body(covers.read(id, coverSize));
  }
}
