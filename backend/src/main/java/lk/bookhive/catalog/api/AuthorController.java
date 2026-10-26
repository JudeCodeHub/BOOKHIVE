package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lk.bookhive.catalog.domain.Author;
import lk.bookhive.catalog.service.AuthorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public author pages (checklist step 204). Their books: GET /search?author={id}. */
@RestController
@RequestMapping("/authors")
@Tag(name = "Catalog")
public class AuthorController {

  private final AuthorService authors;

  public AuthorController(AuthorService authors) {
    this.authors = authors;
  }

  /**
   * @param bookCount books in the catalog with this person as author, editor, etc.
   */
  public record PublicAuthorResponse(UUID id, String name, String bio, long bookCount) {}

  @GetMapping("/{id}")
  @Operation(summary = "An author's name, bio and number of books")
  public PublicAuthorResponse get(@PathVariable UUID id) {
    Author author = authors.get(id);
    return new PublicAuthorResponse(
        author.getId(), author.getName(), author.getBio(), authors.bookCount(id));
  }
}
