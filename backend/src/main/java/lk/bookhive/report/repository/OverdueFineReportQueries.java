package lk.bookhive.report.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-only SQL for the overdue list and the fines report (RPT-4, checklist step 363). Money is
 * read from the fines ledger (fine_transactions), the same source as members' balances.
 */
@Repository
public class OverdueFineReportQueries {

  /**
   * A book still out after its due date.
   *
   * @param fineOwed what's still owed on this loan's overdue fine (0 when none or paid)
   */
  public record OverdueRow(
      UUID loanId,
      String memberNo,
      String memberName,
      String email,
      String phone,
      String title,
      String barcode,
      LocalDate dueDate,
      long daysOverdue,
      BigDecimal fineOwed) {}

  /** How late the overdue books are, and what's owed on them. */
  public record OverdueSummary(
      long total, long upToWeek, long upToMonth, long overMonth, BigDecimal finesOwed) {}

  /** Money moved in one day or month (LKR). */
  public record FinePeriodRow(
      LocalDate start,
      BigDecimal charged,
      BigDecimal collectedCash,
      BigDecimal collectedOnline,
      BigDecimal refunded,
      BigDecimal waived,
      BigDecimal cancelled) {}

  /** Money per kind of fine (overdue, lost, damage, membership fee). */
  public record FineTypeRow(
      String type, BigDecimal charged, BigDecimal collected, BigDecimal waived) {}

  private static final String OVERDUE_FROM =
      """
      FROM loans l
      JOIN copies c ON c.id = l.copy_id
      JOIN books b ON b.id = c.book_id
      JOIN members m ON m.id = l.member_id
      JOIN users u ON u.id = m.user_id
      LEFT JOIN fines f ON f.loan_id = l.id AND f.type = 'OVERDUE'
      WHERE l.status IN ('ACTIVE', 'OVERDUE') AND l.due_date <= :latestDue
      """;

  /** Balance of one fine: charges and refunds add, everything else takes away. */
  private static final String FINE_OWED =
      """
      COALESCE((SELECT SUM(CASE WHEN t.kind IN ('CHARGE', 'REFUND') THEN t.amount
                                ELSE -t.amount END)
                FROM fine_transactions t WHERE t.fine_id = f.id), 0)
      """;

  private final JdbcClient jdbc;

  public OverdueFineReportQueries(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Most overdue first. {@code latestDue}: due on or before this day (today - minDays). */
  public List<OverdueRow> overdue(LocalDate today, LocalDate latestDue, int limit, int offset) {
    return jdbc.sql(
            """
            SELECT l.id, m.member_no, u.full_name, u.email, u.phone, b.title, c.barcode,
                   l.due_date, (CAST(:today AS date) - l.due_date) AS days_overdue,
                   %s AS fine_owed
            %s
            ORDER BY l.due_date, b.title, l.id
            LIMIT :limit OFFSET :offset
            """
                .formatted(FINE_OWED, OVERDUE_FROM))
        .param("today", today)
        .param("latestDue", latestDue)
        .param("limit", limit)
        .param("offset", offset)
        .query(
            (rs, i) ->
                new OverdueRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("member_no"),
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getString("title"),
                    rs.getString("barcode"),
                    rs.getObject("due_date", LocalDate.class),
                    rs.getLong("days_overdue"),
                    rs.getBigDecimal("fine_owed")))
        .list();
  }

  public OverdueSummary overdueSummary(LocalDate today, LocalDate latestDue) {
    return jdbc.sql(
            """
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE CAST(:today AS date) - l.due_date <= 7) AS up_to_week,
                   count(*) FILTER (WHERE CAST(:today AS date) - l.due_date BETWEEN 8 AND 30)
                       AS up_to_month,
                   count(*) FILTER (WHERE CAST(:today AS date) - l.due_date > 30) AS over_month,
                   COALESCE(SUM(%s), 0) AS fines_owed
            %s
            """
                .formatted(FINE_OWED, OVERDUE_FROM))
        .param("today", today)
        .param("latestDue", latestDue)
        .query(
            (rs, i) ->
                new OverdueSummary(
                    rs.getLong("total"),
                    rs.getLong("up_to_week"),
                    rs.getLong("up_to_month"),
                    rs.getLong("over_month"),
                    rs.getBigDecimal("fines_owed")))
        .single();
  }

  /** Every day or month in the range (periods without money are 0). */
  public List<FinePeriodRow> finesByPeriod(
      CirculationReportQueries.Period period,
      LocalDate from,
      LocalDate to,
      Instant start,
      Instant end,
      String zone) {
    return jdbc.sql(
            """
            WITH periods AS (
                SELECT generate_series(date_trunc(:unit, CAST(:from AS date)),
                                       date_trunc(:unit, CAST(:to AS date)),
                                       CAST(:step AS interval))::date AS period
            ),
            t AS (
                SELECT date_trunc(:unit, created_at AT TIME ZONE :zone)::date AS period,
                       SUM(amount) FILTER (WHERE kind = 'CHARGE') AS charged,
                       SUM(amount) FILTER (WHERE kind = 'PAYMENT' AND method = 'CASH') AS cash,
                       SUM(amount) FILTER (WHERE kind = 'PAYMENT' AND method = 'ONLINE') AS online,
                       SUM(amount) FILTER (WHERE kind = 'REFUND') AS refunded,
                       SUM(amount) FILTER (WHERE kind = 'WAIVER') AS waived,
                       SUM(amount) FILTER (WHERE kind = 'CANCELLATION') AS cancelled
                FROM fine_transactions
                WHERE created_at >= :start AND created_at < :end
                GROUP BY 1
            )
            SELECT p.period, COALESCE(t.charged, 0) AS charged, COALESCE(t.cash, 0) AS cash,
                   COALESCE(t.online, 0) AS online, COALESCE(t.refunded, 0) AS refunded,
                   COALESCE(t.waived, 0) AS waived, COALESCE(t.cancelled, 0) AS cancelled
            FROM periods p LEFT JOIN t ON t.period = p.period
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
                new FinePeriodRow(
                    rs.getObject("period", LocalDate.class),
                    rs.getBigDecimal("charged"),
                    rs.getBigDecimal("cash"),
                    rs.getBigDecimal("online"),
                    rs.getBigDecimal("refunded"),
                    rs.getBigDecimal("waived"),
                    rs.getBigDecimal("cancelled")))
        .list();
  }

  /** Per kind of fine, every kind listed (0 when none). Collected = payments - refunds. */
  public List<FineTypeRow> finesByType(Instant start, Instant end) {
    return jdbc.sql(
            """
            SELECT k.type,
                   COALESCE(SUM(t.amount) FILTER (WHERE t.kind = 'CHARGE'), 0) AS charged,
                   COALESCE(SUM(t.amount) FILTER (WHERE t.kind = 'PAYMENT'), 0)
                     - COALESCE(SUM(t.amount) FILTER (WHERE t.kind = 'REFUND'), 0) AS collected,
                   COALESCE(SUM(t.amount) FILTER (WHERE t.kind = 'WAIVER'), 0) AS waived
            FROM (VALUES ('OVERDUE'), ('LOST'), ('DAMAGE'), ('MEMBERSHIP_FEE')) AS k(type)
            LEFT JOIN fines f ON f.type = k.type
            LEFT JOIN fine_transactions t
                   ON t.fine_id = f.id AND t.created_at >= :start AND t.created_at < :end
            GROUP BY k.type
            ORDER BY charged DESC, k.type
            """)
        .param("start", utc(start))
        .param("end", utc(end))
        .query(
            (rs, i) ->
                new FineTypeRow(
                    rs.getString("type"),
                    rs.getBigDecimal("charged"),
                    rs.getBigDecimal("collected"),
                    rs.getBigDecimal("waived")))
        .list();
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
