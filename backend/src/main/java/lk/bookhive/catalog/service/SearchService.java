package lk.bookhive.catalog.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.domain.BookFormat;
import lk.bookhive.catalog.domain.Isbn;
import lk.bookhive.catalog.repository.BookRepository;
import lk.bookhive.catalog.service.BookListService.BookListItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog search for the public site (checklist steps 191-194), on the search columns kept up to
 * date by the V17 triggers:
 *
 * <ul>
 *   <li>Full-text match on title, authors, ISBN, subtitle, categories, description, ranked by field
 *       weight ({@code search_vector}), OR a typo-tolerant trigram match on title + subtitle +
 *       authors ({@code search_text}), so "ondatje" still finds Ondaatje.
 *   <li>A query that is a valid ISBN matches that one book exactly.
 *   <li>Filters: language, format, category (including its subcategories), available now,
 *       publication years. Facet counts tell how many books each filter value would give.
 * </ul>
 *
 * Plain SQL (JdbcClient) because ranking and facets don't map to JPA queries; the page of books is
 * then loaded through JPA like everywhere else.
 */
@Service
public class SearchService {

  private final JdbcClient jdbc;
  private final BookRepository books;
  private final BookListService bookList;

  public SearchService(JdbcClient jdbc, BookRepository books, BookListService bookList) {
    this.jdbc = jdbc;
    this.books = books;
    this.bookList = bookList;
  }

  public enum SearchSort {
    /** Best match first (newest first when there's no query) */
    RELEVANCE,
    NEWEST,
    TITLE,
    YEAR
  }

  /** Everything the search page can ask for. Null / false = no filter. */
  public record SearchQuery(
      String q,
      String language,
      BookFormat format,
      UUID categoryId,
      UUID authorId,
      boolean availableOnly,
      Integer yearFrom,
      Integer yearTo,
      SearchSort sort,
      int page,
      int size) {}

  /** One facet value and how many matching books have it. */
  public record FacetCount(String value, long count) {}

  /**
   * @param availableNow how many of the results can be borrowed right now
   */
  public record Facets(
      List<FacetCount> languages,
      List<FacetCount> formats,
      List<FacetCount> categories,
      long availableNow) {}

  public record SearchResult(Page<BookListItem> books, Facets facets) {}

  /** A filter that can be left out (for its own facet counts). */
  private enum Part {
    LANGUAGE,
    FORMAT,
    CATEGORY,
    AVAILABLE
  }

  @Transactional(readOnly = true)
  public SearchResult search(SearchQuery query) {
    String q = query.q() == null ? "" : query.q().strip();
    Isbn isbn = Isbn.parse(q).orElse(null);
    Map<String, Object> params = new HashMap<>();
    params.put("q", q);
    if (isbn != null) params.put("isbn", isbn.isbn13());
    params.put("language", query.language());
    params.put("format", query.format() == null ? null : query.format().name());
    params.put("category", query.categoryId());
    params.put("author", query.authorId());
    params.put("yearFrom", query.yearFrom());
    params.put("yearTo", query.yearTo());

    String where = where(query, q, isbn, null);
    long total = count("SELECT count(*) FROM books b WHERE " + where, params);

    String order =
        switch (q.isEmpty() && query.sort() == SearchSort.RELEVANCE
            ? SearchSort.NEWEST
            : query.sort()) {
          case RELEVANCE ->
              // Full-text rank counts double; trigram similarity rescues typos
              "(ts_rank_cd(b.search_vector, websearch_to_tsquery('simple', :q)) * 2"
                  + " + word_similarity(:q, b.search_text)) DESC, b.title";
          case NEWEST -> "b.created_at DESC";
          case TITLE -> "b.title";
          case YEAR -> "b.publish_year DESC NULLS LAST, b.title";
        };
    Map<String, Object> pageParams = new HashMap<>(params);
    pageParams.put("limit", query.size());
    pageParams.put("offset", (long) query.page() * query.size());
    List<UUID> ids =
        jdbc.sql(
                "SELECT b.id FROM books b WHERE "
                    + where
                    + " ORDER BY "
                    + order
                    + " LIMIT :limit OFFSET :offset")
            .params(pageParams)
            .query(UUID.class)
            .list();

    List<BookListItem> items = bookList.summaries(inOrder(ids));
    Page<BookListItem> page =
        new PageImpl<>(items, PageRequest.of(query.page(), query.size()), total);
    return new SearchResult(page, facets(query, q, isbn, params));
  }

  /** Facet counts: each one ignores its own filter, so the other choices stay visible. */
  private Facets facets(SearchQuery query, String q, Isbn isbn, Map<String, Object> params) {
    List<FacetCount> languages =
        grouped(
            "SELECT b.language AS value, count(*) AS n FROM books b WHERE "
                + where(query, q, isbn, Part.LANGUAGE)
                + " GROUP BY b.language ORDER BY n DESC",
            params);
    List<FacetCount> formats =
        grouped(
            "SELECT b.format AS value, count(*) AS n FROM books b WHERE "
                + where(query, q, isbn, Part.FORMAT)
                + " GROUP BY b.format ORDER BY n DESC",
            params);
    List<FacetCount> categories =
        grouped(
            "SELECT bc.category_id::text AS value, count(DISTINCT b.id) AS n"
                + " FROM books b JOIN book_categories bc ON bc.book_id = b.id WHERE "
                + where(query, q, isbn, Part.CATEGORY)
                + " GROUP BY bc.category_id ORDER BY n DESC LIMIT 50",
            params);
    // Same filters plus "available now" (unless that filter is already on: then it's the total)
    String available = where(query, q, isbn, Part.AVAILABLE) + " AND " + AVAILABLE_NOW;
    long availableNow = count("SELECT count(*) FROM books b WHERE " + available, params);
    return new Facets(languages, formats, categories, availableNow);
  }

  private static final String AVAILABLE_NOW =
      "EXISTS (SELECT 1 FROM copies c WHERE c.book_id = b.id"
          + " AND c.status = 'AVAILABLE' AND c.lending_type = 'LENDABLE')";

  /** The WHERE clause for the query and filters, optionally without one filter. */
  private static String where(SearchQuery query, String q, Isbn isbn, Part skip) {
    List<String> conditions = new ArrayList<>();
    conditions.add("TRUE");
    if (isbn != null) {
      conditions.add("b.isbn13 = :isbn");
    } else if (!q.isEmpty()) {
      conditions.add(
          "(b.search_vector @@ websearch_to_tsquery('simple', :q) OR :q <% b.search_text)");
    }
    if (query.language() != null && skip != Part.LANGUAGE) {
      conditions.add("b.language = :language");
    }
    if (query.format() != null && skip != Part.FORMAT) {
      conditions.add("b.format = :format");
    }
    if (query.categoryId() != null && skip != Part.CATEGORY) {
      // The category and everything under it ("Literature" includes "Sinhala literature")
      conditions.add(
          "b.id IN (SELECT bc.book_id FROM book_categories bc WHERE bc.category_id IN ("
              + "WITH RECURSIVE sub AS (SELECT id FROM categories WHERE id = :category"
              + " UNION ALL SELECT c.id FROM categories c JOIN sub ON c.parent_id = sub.id)"
              + " SELECT id FROM sub))");
    }
    if (query.authorId() != null) {
      // Author pages: every book they wrote, edited, translated or illustrated
      conditions.add(
          "b.id IN (SELECT ba.book_id FROM book_authors ba WHERE ba.author_id = :author)");
    }
    if (query.availableOnly() && skip != Part.AVAILABLE) {
      conditions.add(AVAILABLE_NOW);
    }
    if (query.yearFrom() != null) {
      conditions.add("b.publish_year >= :yearFrom");
    }
    if (query.yearTo() != null) {
      conditions.add("b.publish_year <= :yearTo");
    }
    return String.join(" AND ", conditions);
  }

  private long count(String sql, Map<String, Object> params) {
    Long n = jdbc.sql(sql).params(params).query(Long.class).single();
    return n == null ? 0 : n;
  }

  private List<FacetCount> grouped(String sql, Map<String, Object> params) {
    return jdbc.sql(sql)
        .params(params)
        .query((rs, row) -> new FacetCount(rs.getString("value"), rs.getLong("n")))
        .list();
  }

  /** The books for these ids, in the same order (findAllById doesn't keep it). */
  private List<Book> inOrder(List<UUID> ids) {
    Map<UUID, Book> byId =
        books.findAllById(ids).stream().collect(Collectors.toMap(Book::getId, Function.identity()));
    return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
  }

  // --- Autocomplete (step 193) ----------------------------------------------------------------

  public enum SuggestionType {
    BOOK,
    AUTHOR
  }

  /**
   * @param detail the first author for a book; empty for an author
   */
  public record Suggestion(SuggestionType type, UUID id, String label, String detail) {}

  /**
   * As-you-type suggestions for the header search box: up to 6 books and 3 authors. Titles that
   * start with the typed text come first; small typos still match.
   */
  @Transactional(readOnly = true)
  public List<Suggestion> suggest(String text) {
    String q = text == null ? "" : text.strip();
    if (q.length() < 2) {
      return List.of();
    }
    Map<String, Object> params = Map.of("q", q, "prefix", escapeLike(q.toLowerCase()) + "%");
    List<Suggestion> result = new ArrayList<>();
    result.addAll(
        jdbc.sql(
                """
                SELECT b.id, b.title, book_author_names(b.id) AS authors
                FROM books b
                WHERE b.search_text LIKE :prefix OR b.search_text LIKE '% ' || :prefix
                   OR :q <% b.search_text
                ORDER BY (lower(b.title) LIKE :prefix) DESC,
                         word_similarity(:q, b.search_text) DESC, b.title
                LIMIT 6
                """)
            .params(params)
            .query(
                (rs, row) ->
                    new Suggestion(
                        SuggestionType.BOOK,
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("authors")))
            .list());
    result.addAll(
        jdbc.sql(
                """
                SELECT a.id, a.name FROM authors a
                WHERE lower(a.name) LIKE :prefix OR lower(a.name) LIKE '% ' || :prefix
                   OR :q <% a.name
                ORDER BY (lower(a.name) LIKE :prefix) DESC, word_similarity(:q, a.name) DESC
                LIMIT 3
                """)
            .params(params)
            .query(
                (rs, row) ->
                    new Suggestion(
                        SuggestionType.AUTHOR,
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        ""))
            .list());
    return result;
  }

  private static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  // --- Home page lists (step 194) -------------------------------------------------------------

  /** The latest titles added to the catalog. */
  @Transactional(readOnly = true)
  public List<BookListItem> newArrivals(int limit) {
    List<UUID> ids =
        jdbc.sql("SELECT id FROM books ORDER BY created_at DESC LIMIT :limit")
            .param("limit", limit)
            .query(UUID.class)
            .list();
    return bookList.summaries(inOrder(ids));
  }

  /**
   * Popular titles. Until the loans table exists (circulation phase) this counts the copies out on
   * loan right now; it then becomes "most borrowed in the last 90 days" (PRD). Books never borrowed
   * don't appear, so the list can be empty.
   */
  @Transactional(readOnly = true)
  public List<BookListItem> popular(int limit) {
    List<UUID> ids =
        jdbc.sql(
                """
                SELECT c.book_id FROM copies c WHERE c.status = 'ON_LOAN'
                GROUP BY c.book_id ORDER BY count(*) DESC LIMIT :limit
                """)
            .param("limit", limit)
            .query(UUID.class)
            .list();
    return bookList.summaries(inOrder(ids));
  }
}
