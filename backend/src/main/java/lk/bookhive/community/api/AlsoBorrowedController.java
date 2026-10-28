package lk.bookhive.community.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.api.BookController.BookSummary;
import lk.bookhive.community.service.AlsoBorrowedService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** "Readers also borrowed" for the book page (checklist step 339); no login needed. */
@RestController
@Tag(name = "Books")
public class AlsoBorrowedController {

  private final AlsoBorrowedService alsoBorrowed;

  public AlsoBorrowedController(AlsoBorrowedService alsoBorrowed) {
    this.alsoBorrowed = alsoBorrowed;
  }

  @GetMapping("/books/{bookId}/also-borrowed")
  @Operation(
      summary = "Up to 8 books this book's readers also borrowed (only when 2+ readers share them)")
  public List<BookSummary> forBook(@PathVariable UUID bookId) {
    return alsoBorrowed.forBook(bookId).stream().map(BookSummary::from).toList();
  }
}
