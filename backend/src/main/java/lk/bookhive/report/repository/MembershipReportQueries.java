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
 * Read-only SQL for membership growth (RPT-5, checklist step 364). The members table keeps each
 * member's current expiry, not a history of every change, so "members at the end of a period"
 * means: approved by then, and their membership (as it stands now) runs past that day.
 */
@Repository
public class MembershipReportQueries {

  /**
   * @param applications applications received (approved or not)
   * @param newMembers applications approved
   * @param membersAtEnd members with a valid membership on the period's last day
   */
  public record GrowthRow(LocalDate start, long applications, long newMembers, long membersAtEnd) {}

  /**
   * @param activeNow ACTIVE and not past their expiry today
   */
  public record TypeRow(
      UUID typeId,
      String code,
      String nameEn,
      String nameSi,
      String nameTa,
      long newMembers,
      long activeNow) {}

  public record Totals(long applications, long newMembers, long activeNow, long pendingNow) {}

  private final JdbcClient jdbc;

  public MembershipReportQueries(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<GrowthRow> byPeriod(
      CirculationReportQueries.Period period,
      LocalDate from,
      LocalDate to,
      Instant start,
      Instant end,
      String zone) {
    return jdbc.sql(
            """
            WITH periods AS (
                SELECT p::date AS period,
                       -- last day of the period, but not after the end of the report
                       LEAST((p + CAST(:step AS interval))::date - 1, CAST(:to AS date))
                           AS last_day
                FROM generate_series(date_trunc(:unit, CAST(:from AS date)),
                                     date_trunc(:unit, CAST(:to AS date)),
                                     CAST(:step AS interval)) AS p
            ),
            a AS (
                SELECT date_trunc(:unit, applied_at AT TIME ZONE :zone)::date AS period,
                       count(*) AS n
                FROM members WHERE applied_at >= :start AND applied_at < :end GROUP BY 1
            ),
            n AS (
                SELECT date_trunc(:unit, approved_at AT TIME ZONE :zone)::date AS period,
                       count(*) AS n
                FROM members WHERE approved_at >= :start AND approved_at < :end GROUP BY 1
            )
            SELECT p.period, COALESCE(a.n, 0) AS applications, COALESCE(n.n, 0) AS new_members,
                   (SELECT count(*) FROM members m
                    WHERE m.approved_at IS NOT NULL
                      AND (m.approved_at AT TIME ZONE :zone)::date <= p.last_day
                      AND m.expires_at >= p.last_day
                      AND m.status IN ('ACTIVE', 'EXPIRED', 'SUSPENDED')) AS members_at_end
            FROM periods p
            LEFT JOIN a ON a.period = p.period
            LEFT JOIN n ON n.period = p.period
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
                new GrowthRow(
                    rs.getObject("period", LocalDate.class),
                    rs.getLong("applications"),
                    rs.getLong("new_members"),
                    rs.getLong("members_at_end")))
        .list();
  }

  /** Every membership type (inactive ones too, if they have members), most members first. */
  public List<TypeRow> byType(Instant start, Instant end, LocalDate today) {
    return jdbc.sql(
            """
            SELECT t.id, t.code, t.name_en, t.name_si, t.name_ta,
                   count(m.id) FILTER (WHERE m.approved_at >= :start AND m.approved_at < :end)
                       AS new_members,
                   count(m.id) FILTER (WHERE m.status = 'ACTIVE' AND m.expires_at >= :today)
                       AS active_now
            FROM membership_types t
            LEFT JOIN members m ON m.membership_type_id = t.id
            GROUP BY t.id
            HAVING t.active OR count(m.id) > 0
            ORDER BY active_now DESC, new_members DESC, t.code
            """)
        .param("start", utc(start))
        .param("end", utc(end))
        .param("today", today)
        .query(
            (rs, i) ->
                new TypeRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("code"),
                    rs.getString("name_en"),
                    rs.getString("name_si"),
                    rs.getString("name_ta"),
                    rs.getLong("new_members"),
                    rs.getLong("active_now")))
        .list();
  }

  public Totals totals(Instant start, Instant end, LocalDate today) {
    return jdbc.sql(
            """
            SELECT
                count(*) FILTER (WHERE applied_at >= :start AND applied_at < :end) AS applications,
                count(*) FILTER (WHERE approved_at >= :start AND approved_at < :end) AS new_members,
                count(*) FILTER (WHERE status = 'ACTIVE' AND expires_at >= :today) AS active_now,
                count(*) FILTER (WHERE status = 'PENDING') AS pending_now
            FROM members
            """)
        .param("start", utc(start))
        .param("end", utc(end))
        .param("today", today)
        .query(
            (rs, i) ->
                new Totals(
                    rs.getLong("applications"),
                    rs.getLong("new_members"),
                    rs.getLong("active_now"),
                    rs.getLong("pending_now")))
        .single();
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
