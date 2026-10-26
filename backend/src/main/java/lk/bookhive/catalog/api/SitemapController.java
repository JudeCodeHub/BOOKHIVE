package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Light lists for the website's sitemap.xml (checklist step 209): just ids and when they last
 * changed, so search engines find every book and author page. Public; paged so a large catalog
 * splits into several sitemap files.
 */
@RestController
@RequestMapping("/sitemap")
@Tag(name = "Catalog")
public class SitemapController {

  private final JdbcClient jdbc;

  public SitemapController(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public record SitemapEntrySummary(UUID id, Instant updatedAt) {}

  @GetMapping("/books")
  @Operation(summary = "Book ids and last change, for sitemap.xml")
  public List<SitemapEntrySummary> books(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "40000") @Min(1) @Max(50000) int size) {
    return page("books", page, size);
  }

  @GetMapping("/authors")
  @Operation(summary = "Author ids and last change, for sitemap.xml")
  public List<SitemapEntrySummary> authors(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "40000") @Min(1) @Max(50000) int size) {
    return page("authors", page, size);
  }

  /** Table names are fixed above, never from the request. */
  private List<SitemapEntrySummary> page(String table, int page, int size) {
    return jdbc.sql(
            "SELECT id, updated_at FROM " + table + " ORDER BY id LIMIT :size OFFSET :offset")
        .param("size", size)
        .param("offset", (long) page * size)
        .query(
            (rs, row) ->
                new SitemapEntrySummary(
                    rs.getObject("id", UUID.class), rs.getTimestamp("updated_at").toInstant()))
        .list();
  }
}
