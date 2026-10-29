package lk.bookhive.report.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-only SQL for "most borrowed" and the weeding list (RPT-3, checklist step 362). Copies that
 * are lost or withdrawn don't count as being in stock. Both can be narrowed to a category and all
 * its sub-categories.
 */
@Repository
public class BookUsageReportQueries {

  /**
   * @param authors author names in their order, comma separated; null when none
   * @param copies copies in stock (not lost or withdrawn)
   */
  public record MostBorrowedRow(
      UUID bookId,
      String title,
      String authors,
      String callNumber,
      long copies,
      long checkouts,
      long borrowers) {}

  /**
   * @param copies lendable copies in stock
   * @param inStockSince when the oldest of them was added
   * @param lastBorrowedOn null = never borrowed at all
   */
  public record NotBorrowedRow(
      UUID bookId,
      String title,
      String authors,
      String callNumber,
      long copies,
      LocalDate inStockSince,
      LocalDate lastBorrowedOn) {}

  /** Books in this category or any category below it (all books when null). */
  private static final String IN_CATEGORY =
      """
      (CAST(:categoryId AS uuid) IS NULL OR b.id IN (
          WITH RECURSIVE tree AS (
              SELECT id FROM categories WHERE id = CAST(:categoryId AS uuid)
              UNION ALL
              SELECT c.id FROM categories c JOIN tree t ON c.parent_id = t.id
          )
          SELECT bc.book_id FROM book_categories bc JOIN tree t ON t.id = bc.category_id))
      """;

  private static final String AUTHORS =
      """
      (SELECT string_agg(a.name, ', ' ORDER BY ba.position)
       FROM book_authors ba JOIN authors a ON a.id = ba.author_id
       WHERE ba.book_id = b.id AND ba.role = 'AUTHOR')
      """;

  private final JdbcClient jdbc;

  public BookUsageReportQueries(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** The most borrowed titles between start and end, most loans first. */
  public List<MostBorrowedRow> mostBorrowed(
      Instant start, Instant end, UUID categoryId, int limit) {
    return jdbc.sql(
            """
            SELECT b.id, b.title, b.call_number, %s AS authors,
                   count(l.id) AS checkouts, count(DISTINCT l.member_id) AS borrowers,
                   (SELECT count(*) FROM copies s
                    WHERE s.book_id = b.id AND s.status NOT IN ('LOST', 'WITHDRAWN')) AS copies
            FROM loans l
            JOIN copies c ON c.id = l.copy_id
            JOIN books b ON b.id = c.book_id
            WHERE l.checked_out_at >= :start AND l.checked_out_at < :end AND %s
            GROUP BY b.id
            ORDER BY checkouts DESC, borrowers DESC, b.title
            LIMIT :limit
            """
                .formatted(AUTHORS, IN_CATEGORY))
        .param("start", utc(start))
        .param("end", utc(end))
        .param("categoryId", categoryId)
        .param("limit", limit)
        .query(
            (rs, i) ->
                new MostBorrowedRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("title"),
                    rs.getString("authors"),
                    rs.getString("call_number"),
                    rs.getLong("copies"),
                    rs.getLong("checkouts"),
                    rs.getLong("borrowers")))
        .list();
  }

  /**
   * The weeding list: books with lendable copies on the shelf since before {@code since} that
   * nobody has borrowed since then. Never-borrowed first, then the longest unborrowed; by call
   * number after that, so the list follows the shelves.
   */
  public List<NotBorrowedRow> notBorrowedSince(
      Instant since, LocalDate sinceDay, UUID categoryId, int limit, int offset) {
    return jdbc.sql(
            NOT_BORROWED_SELECT
                + " ORDER BY last_borrowed NULLS FIRST, b.call_number NULLS LAST, b.title LIMIT :limit OFFSET :offset")
        .param("since", utc(since))
        .param("sinceDay", sinceDay)
        .param("categoryId", categoryId)
        .param("limit", limit)
        .param("offset", offset)
        .query(
            (rs, i) ->
                new NotBorrowedRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("title"),
                    rs.getString("authors"),
                    rs.getString("call_number"),
                    rs.getLong("copies"),
                    rs.getObject("in_stock_since", LocalDate.class),
                    rs.getObject("last_borrowed", LocalDate.class)))
        .list();
  }

  public long countNotBorrowedSince(Instant since, LocalDate sinceDay, UUID categoryId) {
    return jdbc.sql("SELECT count(*) FROM (" + NOT_BORROWED_SELECT + ") x")
        .param("since", utc(since))
        .param("sinceDay", sinceDay)
        .param("categoryId", categoryId)
        .query(Long.class)
        .single();
  }

  private static final String NOT_BORROWED_SELECT =
      """
      SELECT b.id, b.title, b.call_number, %s AS authors, s.copies, s.in_stock_since,
             (SELECT max(l.checked_out_at AT TIME ZONE 'Asia/Colombo')::date
              FROM loans l JOIN copies c ON c.id = l.copy_id
              WHERE c.book_id = b.id) AS last_borrowed
      FROM books b
      JOIN (SELECT book_id, count(*) AS copies,
                   min(COALESCE(acquired_at, (created_at AT TIME ZONE 'Asia/Colombo')::date))
                       AS in_stock_since
            FROM copies
            WHERE status NOT IN ('LOST', 'WITHDRAWN') AND lending_type = 'LENDABLE'
            GROUP BY book_id) s ON s.book_id = b.id
      WHERE s.in_stock_since < :sinceDay
        AND NOT EXISTS (SELECT 1 FROM loans l JOIN copies c ON c.id = l.copy_id
                        WHERE c.book_id = b.id AND l.checked_out_at >= :since)
        AND %s
      """
          .formatted(AUTHORS, IN_CATEGORY);

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
