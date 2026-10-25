package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.domain.Author;
import lk.bookhive.catalog.service.AuthorService;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Author management for cataloguing (librarians and admins, via /staff/**). */
@RestController
@RequestMapping("/staff/authors")
@Tag(name = "Staff: authors")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffAuthorController {

  private final AuthorService authors;

  public StaffAuthorController(AuthorService authors) {
    this.authors = authors;
  }

  public record AuthorRequest(
      @NotBlank @Size(max = 200) String name, @Size(max = 5000) String bio) {}

  public record AuthorResponse(UUID id, String name, String bio) {

    static AuthorResponse from(Author author) {
      return new AuthorResponse(author.getId(), author.getName(), author.getBio());
    }
  }

  /**
   * @param bookCount books this author is on; 0 means it can be deleted
   */
  public record AuthorDetailResponse(UUID id, String name, String bio, long bookCount) {}

  @GetMapping
  @Operation(summary = "Authors A–Z, optionally filtered by part of the name")
  public PageResponse<AuthorResponse> list(
      @RequestParam(defaultValue = "") @Size(max = 200) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(authors.list(q, page, size), AuthorResponse::from);
  }

  @GetMapping("/suggest")
  @Operation(summary = "Best matches for the author picker (typo-tolerant, max 10)")
  public List<AuthorResponse> suggest(@RequestParam @Size(max = 200) String q) {
    return authors.suggest(q).stream().map(AuthorResponse::from).toList();
  }

  @GetMapping("/{id}")
  @Operation(summary = "One author, with how many books they're on")
  public AuthorDetailResponse get(@PathVariable UUID id) {
    Author author = authors.get(id);
    return new AuthorDetailResponse(
        author.getId(), author.getName(), author.getBio(), authors.bookCount(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add an author")
  public AuthorResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthorRequest request) {
    return AuthorResponse.from(authors.create(request.name(), request.bio(), staffId(jwt)));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Edit an author (the change shows on all their books)")
  public AuthorResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody AuthorRequest request) {
    return AuthorResponse.from(authors.update(id, request.name(), request.bio(), staffId(jwt)));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete an author who is on no books (409 AUTHOR_HAS_BOOKS otherwise)")
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    authors.delete(id, staffId(jwt));
  }

  private static UUID staffId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
