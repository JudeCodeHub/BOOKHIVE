package lk.bookhive.report.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.report.repository.CirculationReportQueries;
import lk.bookhive.report.repository.CirculationReportQueries.CategoryRow;
import lk.bookhive.report.repository.CirculationReportQueries.Period;
import lk.bookhive.report.repository.CirculationReportQueries.PeriodRow;
import lk.bookhive.report.repository.CirculationReportQueries.Totals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Circulation report (RPT-2, checklist step 361): loans and returns per day, per month, or per
 * category, between two library days (both included). Ranges are capped so one request can't scan
 * years of loans day by day.
 */
@Service
public class CirculationReportService {

  public enum GroupBy {
    DAY,
    MONTH,
    CATEGORY
  }

  /** Longest range per grouping, in days. */
  static final int MAX_DAYS_BY_DAY = 366;

  static final int MAX_DAYS_OTHERWISE = 5 * 366;

  /** Exactly one of {@code periods} / {@code categories} is filled, by {@code groupBy}. */
  public record CirculationReport(
      LocalDate from,
      LocalDate to,
      GroupBy groupBy,
      Totals totals,
      List<PeriodRow> periods,
      List<CategoryRow> categories) {}

  private final CirculationReportQueries queries;

  public CirculationReportService(CirculationReportQueries queries) {
    this.queries = queries;
  }

  @Transactional(readOnly = true)
  public CirculationReport report(LocalDate from, LocalDate to, GroupBy groupBy) {
    checkRange(from, to, groupBy == GroupBy.DAY ? MAX_DAYS_BY_DAY : MAX_DAYS_OTHERWISE);
    Instant start = from.atStartOfDay(LibraryTime.ZONE).toInstant();
    Instant end = to.plusDays(1).atStartOfDay(LibraryTime.ZONE).toInstant();
    String zone = LibraryTime.ZONE.getId();
    Totals totals = queries.totals(start, end);
    return switch (groupBy) {
      case DAY ->
          new CirculationReport(
              from,
              to,
              groupBy,
              totals,
              queries.byPeriod(Period.DAY, from, to, start, end, zone),
              List.of());
      case MONTH ->
          new CirculationReport(
              from,
              to,
              groupBy,
              totals,
              queries.byPeriod(Period.MONTH, from, to, start, end, zone),
              List.of());
      case CATEGORY ->
          new CirculationReport(
              from, to, groupBy, totals, List.of(), queries.byCategory(start, end));
    };
  }

  /** from <= to, and not longer than {@code maxDays}. Shared by the other reports. */
  public static void checkRange(LocalDate from, LocalDate to, int maxDays) {
    if (to.isBefore(from)) {
      throw new FieldValidationException(
          "to", "RANGE_INVALID", "The end date is before the start.");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > maxDays) {
      throw new FieldValidationException(
          "to", "RANGE_TOO_LONG", "Choose at most " + maxDays + " days, or group by month.");
    }
  }
}
