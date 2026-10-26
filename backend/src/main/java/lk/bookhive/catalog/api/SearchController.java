package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lk.bookhive.catalog.api.BookController.BookSummary;
import lk.bookhive.catalog.domain.BookFormat;
import lk.bookhive.catalog.service.CatalogCacheKeys;
import lk.bookhive.catalog.service.SearchService;
import lk.bookhive.catalog.service.SearchService.FacetCount;
import lk.bookhive.catalog.service.SearchService.SearchQuery;
import lk.bookhive.catalog.service.SearchService.SearchResult;
import lk.bookhive.catalog.service.SearchService.SearchSort;
import lk.bookhive.catalog.service.SearchService.Suggestion;
import lk.bookhive.catalog.service.SearchService.SuggestionType;
import lk.bookhive.common.cache.PublicCache;
import lk.bookhive.common.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

/** Public catalog search (checklist steps 191-193). */
@RestController
@RequestMapping("/search")
@Tag(name = "Catalog")
public class SearchController {

  private final SearchService search;
  private final PublicCache cache;

  public SearchController(SearchService search, PublicCache cache) {
    this.search = search;
    this.cache = cache;
  }

  /** One facet value (a language code, a format, a category id) and its number of books. */
  public record FacetSummary(String value, long count) {

    static List<FacetSummary> from(List<FacetCount> counts) {
      return counts.stream().map(c -> new FacetSummary(c.value(), c.count())).toList();
    }
  }

  /**
   * @param availableNow how many of the results can be borrowed right now
   */
  public record FacetsResponse(
      List<FacetSummary> languages,
      List<FacetSummary> formats,
      List<FacetSummary> categories,
      long availableNow) {}

  /**
   * @param detail a book's authors; empty for an author
   */
  public record SuggestionSummary(SuggestionType type, UUID id, String label, String detail) {}

  public record SearchResponse(PageResponse<BookSummary> results, FacetsResponse facets) {}

  @GetMapping
  @Operation(
      summary = "Search books: words, typos and ISBNs; filters, facet counts, sorting, paging")
  public SearchResponse search(
      @RequestParam(defaultValue = "") @Size(max = 200) String q,
      @RequestParam(required = false) @Pattern(regexp = "^[a-z]{2,3}$") String language,
      @RequestParam(required = false) BookFormat format,
      @RequestParam(required = false) UUID category,
      @RequestParam(defaultValue = "false") boolean available,
      @RequestParam(required = false) @Min(1000) @Max(2100) Integer yearFrom,
      @RequestParam(required = false) @Min(1000) @Max(2100) Integer yearTo,
      @RequestParam(defaultValue = "RELEVANCE") SearchSort sort,
      @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(60) int size) {
    SearchResult result =
        search.search(
            new SearchQuery(
                q, language, format, category, available, yearFrom, yearTo, sort, page, size));
    return new SearchResponse(
        PageResponse.from(result.books(), BookSummary::from),
        new FacetsResponse(
            FacetSummary.from(result.facets().languages()),
            FacetSummary.from(result.facets().formats()),
            FacetSummary.from(result.facets().categories()),
            result.facets().availableNow()));
  }

  @GetMapping("/suggest")
  @Operation(summary = "As-you-type suggestions: up to 6 books and 3 authors")
  public List<SuggestionSummary> suggest(@RequestParam @Size(max = 100) String q) {
    String key = q.strip().toLowerCase(Locale.ROOT);
    if (key.length() < 2) {
      return List.of();
    }
    // Many people type the same first letters; a short cache saves most of the work
    List<Suggestion> suggestions =
        cache.get(
            CatalogCacheKeys.SUGGEST_PREFIX + key,
            CatalogCacheKeys.SUGGEST_TTL,
            new TypeReference<>() {},
            () -> search.suggest(key));
    return suggestions.stream()
        .map(x -> new SuggestionSummary(x.type(), x.id(), x.label(), x.detail()))
        .toList();
  }
}
