package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lk.bookhive.catalog.api.BookResponses.BookResponse;
import lk.bookhive.catalog.service.BookService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public catalog: anyone can look at a book (PRD §10). */
@RestController
@RequestMapping("/books")
@Tag(name = "Catalog")
public class BookController {

  private final BookService books;

  public BookController(BookService books) {
    this.books = books;
  }

  @GetMapping("/{id}")
  @Operation(summary = "One book with its authors, publisher and categories")
  public BookResponse get(@PathVariable UUID id) {
    return BookResponse.from(books.get(id));
  }
}
