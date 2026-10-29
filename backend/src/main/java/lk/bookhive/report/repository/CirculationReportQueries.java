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
 * Read-only SQL for the circulation report (RPT-2, checklist step 361). Reports span several
 * modules' tables (loans, copies, categories), so they're plain SQL owned by the report module:
 * nothing here writes, and other modules never call it. Days and months are Sri Lankan ones.
 */
@Repository
public class CirculationReportQueries {

  /** How rows are grouped over time. */
  public enum Period {
    DAY("day", "1 day"),
    MONTH("month", "1 month");

    final String unit;
    final String step;

    Period(String unit, String step) {
      this.unit = unit;
      this.step = step;
    }
  }

  /**
   * One day or month.
   *
   * @param start its first day ("2026-10-01" for October)
   * @param borrowers different members who borrowed in it
   */
  public record PeriodRow(LocalDate start, long checkouts, long returns, long borrowers) {}

  /** One top-level category (DDC main class); a book in two classes counts in both. */
  public record CategoryRow(
      UUID categoryId,
      String nameEn,
      String nameSi,
      String nameTa,
      String ddcCode,
      long checkouts,
      long borrowers) {}

  /** For the whole range: borrowers are counted once even if they borrowed every day. */
  public record Totals(long checkouts, long returns, long borrowers, long uncategorised) {}

  private final JdbcClient jdbc;

  public CirculationReportQueries(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Every day or month from {@code from} to {@code to}; periods without activity are 0. */
  public List<PeriodRow> byPeriod(
      Period period, LocalDate from, LocalDate to, Instant start, Instant end, String zone) {
    return jdbc.sql(
            """
            WITH periods AS (
                SELECT generate_series(date_trunc(:unit, CAST(:from AS date)),
                                       date_trunc(:unit, CAST(:to AS date)),
                                       CAST(:step AS interval))::date AS period
            ),
            c AS (
                SELECT date_trunc(:unit, checked_out_at AT TIME ZONE :zone)::date AS period,
                       count(*) AS n, count(DISTINCT member_id) AS borrowers
                FROM loans
                WHERE checked_out_at >= :start AND checked_out_at < :end
                GROUP BY 1
            ),
            r AS (
                SELECT date_trunc(:unit, returned_at AT TIME ZONE :zone)::date AS period,
                       count(*) AS n
                FROM loans
                WHERE returned_at >= :start AND returned_at < :end
                GROUP BY 1
            )
            SELECT p.period, COALESCE(c.n, 0) AS checkouts, COALESCE(r.n, 0) AS returns,
                   COALESCE(c.borrowers, 0) AS borrowers
            FROM periods p
            LEFT JOIN c ON c.period = p.period
            LEFT JOIN r ON r.period = p.period
            ORDER BY p.period
            """)
        .param("unit", period.unit)
        .param("step", period.step)
        .param("from", from)
        .param("to", to)
        .param("start", utc(start))
        .param("end", utc(end))
        .param("zone", zone)
        .query(
            (rs, i) ->
                new PeriodRow(
                    rs.getObject("period", LocalDate.class),
                    rs.getLong("checkouts"),
                    rs.getLong("returns"),
                    rs.getLong("borrowers")))
        .list();
  }

  /** Loans per top-level category, most borrowed first; categories nobody borrowed from are 0. */
  public List<CategoryRow> byCategory(Instant start, Instant end) {
    return jdbc.sql(
            """
            WITH RECURSIVE roots AS (
                SELECT id, id AS root_id FROM categories WHERE parent_id IS NULL
                UNION ALL
                SELECT c.id, r.root_id FROM categories c JOIN roots r ON c.parent_id = r.id
            ),
            lc AS (
                SELECT DISTINCT l.id, l.member_id, r.root_id
                FROM loans l
                JOIN copies cp ON cp.id = l.copy_id
                JOIN book_categories bc ON bc.book_id = cp.book_id
                JOIN roots r ON r.id = bc.category_id
                WHERE l.checked_out_at >= :start AND l.checked_out_at < :end
            )
            SELECT top.id, top.name_en, top.name_si, top.name_ta, top.ddc_code,
                   count(lc.id) AS checkouts, count(DISTINCT lc.member_id) AS borrowers
            FROM categories top
            LEFT JOIN lc ON lc.root_id = top.id
            WHERE top.parent_id IS NULL
            GROUP BY top.id
            ORDER BY checkouts DESC, top.ddc_code NULLS LAST, top.name_en
            """)
        .param("start", utc(start))
        .param("end", utc(end))
        .query(
            (rs, i) ->
                new CategoryRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("name_en"),
                    rs.getString("name_si"),
                    rs.getString("name_ta"),
                    rs.getString("ddc_code"),
                    rs.getLong("checkouts"),
                    rs.getLong("borrowers")))
        .list();
  }

  public Totals totals(Instant start, Instant end) {
    return jdbc.sql(
            """
            SELECT
                (SELECT count(*) FROM loans
                 WHERE checked_out_at >= :start AND checked_out_at < :end) AS checkouts,
                (SELECT count(*) FROM loans
                 WHERE returned_at >= :start AND returned_at < :end) AS returns,
                (SELECT count(DISTINCT member_id) FROM loans
                 WHERE checked_out_at >= :start AND checked_out_at < :end) AS borrowers,
                (SELECT count(*) FROM loans l JOIN copies cp ON cp.id = l.copy_id
                 WHERE l.checked_out_at >= :start AND l.checked_out_at < :end
                   AND NOT EXISTS (SELECT 1 FROM book_categories bc
                                   WHERE bc.book_id = cp.book_id)) AS uncategorised
            """)
        .param("start", utc(start))
        .param("end", utc(end))
        .query(
            (rs, i) ->
                new Totals(
                    rs.getLong("checkouts"),
                    rs.getLong("returns"),
                    rs.getLong("borrowers"),
                    rs.getLong("uncategorised")))
        .single();
  }

  /** The JDBC driver takes timestamps as OffsetDateTime, not Instant. */
  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
