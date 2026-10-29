package lk.bookhive.report.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-only SQL over the catalog's copies for stock-takes (step 365): which copies a session's
 * scope covers, and which of the expected ones weren't scanned.
 */
@Repository
public class StocktakeQueries {

  /** A copy found by its barcode, and whether it's in the session's scope. */
  public record ScannedCopy(UUID copyId, String status, boolean inScope) {}

  /** A copy that should be on the shelf but wasn't scanned. */
  public record MissingCopy(
      UUID copyId,
      String barcode,
      UUID bookId,
      String title,
      String callNumber,
      String shelfLocation) {}

  /**
   * Copies in scope: in the category or below it (when set) and on a shelf whose location starts
   * with the prefix as whole words (when set; ignoring case): "Non-fiction 500s" covers
   * "Non-fiction 500s, shelf 2", but "Non-fiction 500s, shelf 1" doesn't cover "... shelf 11".
   */
  private static final String IN_SCOPE =
      """
      (CAST(:categoryId AS uuid) IS NULL OR c.book_id IN (
          WITH RECURSIVE tree AS (
              SELECT id FROM categories WHERE id = CAST(:categoryId AS uuid)
              UNION ALL
              SELECT k.id FROM categories k JOIN tree t ON k.parent_id = t.id
          )
          SELECT bc.book_id FROM book_categories bc JOIN tree t ON t.id = bc.category_id))
      AND (CAST(:shelfPrefix AS text) IS NULL
           OR lower(c.shelf_location) LIKE lower(CAST(:shelfPrefix AS text))
           OR lower(c.shelf_location) LIKE lower(CAST(:shelfPrefix AS text)) || ' %'
           OR lower(c.shelf_location) LIKE lower(CAST(:shelfPrefix AS text)) || ',%')
      """;

  /** Copies that should be on the shelf now: AVAILABLE and in scope, not yet scanned. */
  private static final String MISSING_FROM =
      """
      FROM copies c JOIN books b ON b.id = c.book_id
      WHERE c.status = 'AVAILABLE' AND %s
        AND NOT EXISTS (SELECT 1 FROM stocktake_scans s
                        WHERE s.session_id = :sessionId AND s.copy_id = c.id)
      """
          .formatted(IN_SCOPE);

  private final JdbcClient jdbc;

  public StocktakeQueries(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<ScannedCopy> findByBarcode(String barcode, UUID categoryId, String shelfPrefix) {
    return jdbc.sql(
            "SELECT c.id, c.status, (%s) AS in_scope FROM copies c WHERE c.barcode = :barcode"
                .formatted(IN_SCOPE))
        .param("barcode", barcode)
        .param("categoryId", categoryId)
        .param("shelfPrefix", escapeLike(shelfPrefix))
        .query(
            (rs, i) ->
                new ScannedCopy(
                    rs.getObject("id", UUID.class),
                    rs.getString("status"),
                    rs.getBoolean("in_scope")))
        .optional();
  }

  /** AVAILABLE copies in scope: what the shelves should hold right now. */
  public long countExpected(UUID categoryId, String shelfPrefix) {
    return jdbc.sql("SELECT count(*) FROM copies c WHERE c.status = 'AVAILABLE' AND " + IN_SCOPE)
        .param("categoryId", categoryId)
        .param("shelfPrefix", escapeLike(shelfPrefix))
        .query(Long.class)
        .single();
  }

  /** Expected copies found by scanning (scanned in scope, and still AVAILABLE). */
  public long countFound(UUID sessionId, UUID categoryId, String shelfPrefix) {
    return jdbc.sql(
            """
            SELECT count(*) FROM copies c
            WHERE c.status = 'AVAILABLE' AND %s
              AND EXISTS (SELECT 1 FROM stocktake_scans s
                          WHERE s.session_id = :sessionId AND s.copy_id = c.id)
            """
                .formatted(IN_SCOPE))
        .param("sessionId", sessionId)
        .param("categoryId", categoryId)
        .param("shelfPrefix", escapeLike(shelfPrefix))
        .query(Long.class)
        .single();
  }

  /** In shelf order (location, call number), so staff can walk along and look again. */
  public List<MissingCopy> missing(
      UUID sessionId, UUID categoryId, String shelfPrefix, int limit, int offset) {
    return jdbc.sql(
            """
            SELECT c.id, c.barcode, b.id AS book_id, b.title, b.call_number, c.shelf_location
            %s
            ORDER BY c.shelf_location NULLS LAST, b.call_number NULLS LAST, b.title, c.barcode
            LIMIT :limit OFFSET :offset
            """
                .formatted(MISSING_FROM))
        .param("sessionId", sessionId)
        .param("categoryId", categoryId)
        .param("shelfPrefix", escapeLike(shelfPrefix))
        .param("limit", limit)
        .param("offset", offset)
        .query(
            (rs, i) ->
                new MissingCopy(
                    rs.getObject("id", UUID.class),
                    rs.getString("barcode"),
                    rs.getObject("book_id", UUID.class),
                    rs.getString("title"),
                    rs.getString("call_number"),
                    rs.getString("shelf_location")))
        .list();
  }

  public long countMissing(UUID sessionId, UUID categoryId, String shelfPrefix) {
    return jdbc.sql("SELECT count(*) " + MISSING_FROM)
        .param("sessionId", sessionId)
        .param("categoryId", categoryId)
        .param("shelfPrefix", escapeLike(shelfPrefix))
        .query(Long.class)
        .single();
  }

  /** Titles and shelves of copies, for showing scans that need a look. */
  public record CopyLabel(UUID copyId, String title, String shelfLocation) {}

  public List<CopyLabel> labels(List<UUID> copyIds) {
    if (copyIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            SELECT c.id, b.title, c.shelf_location
            FROM copies c JOIN books b ON b.id = c.book_id WHERE c.id IN (:ids)
            """)
        .param("ids", copyIds)
        .query(
            (rs, i) ->
                new CopyLabel(
                    rs.getObject("id", UUID.class),
                    rs.getString("title"),
                    rs.getString("shelf_location")))
        .list();
  }

  /** The prefix is matched literally: % and _ in a shelf name aren't wildcards. */
  private static String escapeLike(String prefix) {
    return prefix == null
        ? null
        : prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
