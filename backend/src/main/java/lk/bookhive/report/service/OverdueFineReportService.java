package lk.bookhive.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.report.repository.CirculationReportQueries.Period;
import lk.bookhive.report.repository.OverdueFineReportQueries;
import lk.bookhive.report.repository.OverdueFineReportQueries.FinePeriodRow;
import lk.bookhive.report.repository.OverdueFineReportQueries.FineTypeRow;
import lk.bookhive.report.repository.OverdueFineReportQueries.OverdueRow;
import lk.bookhive.report.repository.OverdueFineReportQueries.OverdueSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Overdue books right now, and money from fines over a range (RPT-4, checklist step 363).
 * "Collected" is payments minus refunds; cash and online are shown apart for the cash drawer.
 */
@Service
public class OverdueFineReportService {

  public record OverduePage(
      LocalDate today, OverdueSummary summary, List<OverdueRow> loans, int page, int size) {}

  /** Totals over the whole range (LKR). */
  public record FineTotals(
      BigDecimal charged,
      BigDecimal collectedCash,
      BigDecimal collectedOnline,
      BigDecimal refunded,
      BigDecimal collected,
      BigDecimal waived,
      BigDecimal cancelled) {}

  public record FineReport(
      LocalDate from,
      LocalDate to,
      Period groupBy,
      FineTotals totals,
      List<FinePeriodRow> periods,
      List<FineTypeRow> byType) {}

  private final OverdueFineReportQueries queries;
  private final LibraryTime time;

  public OverdueFineReportService(OverdueFineReportQueries queries, LibraryTime time) {
    this.queries = queries;
    this.time = time;
  }

  /**
   * @param minDays only loans at least this many days overdue (1 = everything overdue)
   */
  @Transactional(readOnly = true)
  public OverduePage overdue(int minDays, int page, int size) {
    LocalDate today = time.today();
    LocalDate latestDue = today.minusDays(minDays);
    return new OverduePage(
        today,
        queries.overdueSummary(today, latestDue),
        queries.overdue(today, latestDue, size, page * size),
        page,
        size);
  }

  @Transactional(readOnly = true)
  public FineReport fines(LocalDate from, LocalDate to, Period groupBy) {
    CirculationReportService.checkRange(
        from,
        to,
        groupBy == Period.DAY
            ? CirculationReportService.MAX_DAYS_BY_DAY
            : CirculationReportService.MAX_DAYS_OTHERWISE);
    Instant start = from.atStartOfDay(LibraryTime.ZONE).toInstant();
    Instant end = to.plusDays(1).atStartOfDay(LibraryTime.ZONE).toInstant();
    List<FinePeriodRow> periods =
        queries.finesByPeriod(groupBy, from, to, start, end, LibraryTime.ZONE.getId());

    BigDecimal charged = BigDecimal.ZERO;
    BigDecimal cash = BigDecimal.ZERO;
    BigDecimal online = BigDecimal.ZERO;
    BigDecimal refunded = BigDecimal.ZERO;
    BigDecimal waived = BigDecimal.ZERO;
    BigDecimal cancelled = BigDecimal.ZERO;
    for (FinePeriodRow p : periods) {
      charged = charged.add(p.charged());
      cash = cash.add(p.collectedCash());
      online = online.add(p.collectedOnline());
      refunded = refunded.add(p.refunded());
      waived = waived.add(p.waived());
      cancelled = cancelled.add(p.cancelled());
    }
    FineTotals totals =
        new FineTotals(
            charged,
            cash,
            online,
            refunded,
            cash.add(online).subtract(refunded),
            waived,
            cancelled);
    return new FineReport(from, to, groupBy, totals, periods, queries.finesByType(start, end));
  }
}
