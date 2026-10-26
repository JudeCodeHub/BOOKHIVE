package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lk.bookhive.catalog.api.BookResponses.BookResponse;
import lk.bookhive.catalog.domain.AuthorRole;
import lk.bookhive.catalog.domain.BookFormat;
import lk.bookhive.catalog.service.BookService;
import lk.bookhive.catalog.service.BookService.BookForm;
import lk.bookhive.catalog.service.BookService.ContributorForm;
import lk.bookhive.catalog.service.CatalogImportService;
import lk.bookhive.catalog.service.CatalogImportService.ImportReport;
import lk.bookhive.catalog.service.CoverService;
import lk.bookhive.common.config.OpenApiConfig;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Cataloguing titles (librarians and admins). Reading a book: GET /books/{id}. */
@RestController
@RequestMapping("/staff/books")
@Tag(name = "Staff: books")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffBookController {

  private final BookService books;
  private final CoverService covers;
  private final CatalogImportService importer;

  public StaffBookController(
      BookService books, CoverService covers, CatalogImportService importer) {
    this.books = books;
    this.covers = covers;
    this.importer = importer;
  }

  public record ContributorRequest(@NotNull UUID authorId, AuthorRole role) {}

  /**
   * @param isbn ISBN-10 or ISBN-13, hyphens allowed; empty if the book has none
   * @param language ISO 639-1 code, e.g. si, ta, en
   * @param contributors in the order printed on the cover; role defaults to AUTHOR
   */
  public record BookRequest(
      @NotBlank @Size(max = 300) String title,
      @Size(max = 300) String subtitle,
      @Size(max = 20) String isbn,
      @Size(max = 50) String edition,
      @Min(1000) @Max(2100) Short publishYear,
      @Min(1) @Max(100000) Integer pages,
      @NotBlank @Pattern(regexp = "^[a-zA-Z]{2,3}$", message = "a language code like si")
          String language,
      @Size(max = 10000) String description,
      BookFormat format,
      @Size(max = 50) String callNumber,
      UUID publisherId,
      @Size(max = 20) List<@Valid ContributorRequest> contributors,
      @Size(max = 10) Set<UUID> categoryIds) {

    BookForm form() {
      return new BookForm(
          title,
          subtitle,
          isbn,
          edition,
          publishYear,
          pages,
          language,
          description,
          format,
          callNumber,
          publisherId,
          contributors == null
              ? List.of()
              : contributors.stream()
                  .map(c -> new ContributorForm(c.authorId(), c.role()))
                  .toList(),
          categoryIds);
    }
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Catalogue a new title (409 BOOK_ISBN_EXISTS if the ISBN is taken)")
  public BookResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BookRequest request) {
    return BookResponse.from(books.create(request.form(), staffId(jwt)));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Edit a title; authors and categories are replaced by the lists sent")
  public BookResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody BookRequest request) {
    return BookResponse.from(books.update(id, request.form(), staffId(jwt)));
  }

  @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Import books and copies from CSV (dryRun=true: preview, nothing saved)")
  public ImportReport importCsv(
      @AuthenticationPrincipal Jwt jwt,
      @RequestPart("file") MultipartFile file,
      @RequestParam(defaultValue = "false") boolean dryRun)
      throws IOException {
    return importer.importCsv(file.getInputStream(), staffId(jwt), dryRun);
  }

  @GetMapping(path = "/import/template.csv", produces = "text/csv")
  @Operation(summary = "The CSV template: column names and one example row")
  public ResponseEntity<String> importTemplate() {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("bookhive-import-template.csv")
                .build()
                .toString())
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(CatalogImportService.template());
  }

  @PostMapping(path = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload or replace the cover (JPG or PNG, up to 5 MB)")
  public BookResponse uploadCover(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestPart("cover") MultipartFile cover)
      throws IOException {
    covers.upload(id, cover.getInputStream(), cover.getSize(), staffId(jwt));
    return BookResponse.from(books.get(id));
  }

  @DeleteMapping("/{id}/cover")
  @Operation(summary = "Remove the cover")
  public BookResponse removeCover(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    covers.remove(id, staffId(jwt));
    return BookResponse.from(books.get(id));
  }

  private static UUID staffId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
