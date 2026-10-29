package lk.bookhive.report.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.report.repository.CirculationReportQueries.Period;
import lk.bookhive.report.repository.MembershipReportQueries;
import lk.bookhive.report.repository.MembershipReportQueries.GrowthRow;
import lk.bookhive.report.repository.MembershipReportQueries.Totals;
import lk.bookhive.report.repository.MembershipReportQueries.TypeRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Membership growth by period and by membership type (RPT-5, checklist step 364). */
@Service
public class MembershipReportService {

  public record MembershipReport(
      LocalDate from,
      LocalDate to,
      Period groupBy,
      Totals totals,
      List<GrowthRow> periods,
      List<TypeRow> byType) {}

  private final MembershipReportQueries queries;
  private final LibraryTime time;

  public MembershipReportService(MembershipReportQueries queries, LibraryTime time) {
    this.queries = queries;
    this.time = time;
  }

  @Transactional(readOnly = true)
  public MembershipReport growth(LocalDate from, LocalDate to, Period groupBy) {
    CirculationReportService.checkRange(
        from,
        to,
        groupBy == Period.DAY
            ? CirculationReportService.MAX_DAYS_BY_DAY
            : CirculationReportService.MAX_DAYS_OTHERWISE);
    Instant start = from.atStartOfDay(LibraryTime.ZONE).toInstant();
    Instant end = to.plusDays(1).atStartOfDay(LibraryTime.ZONE).toInstant();
    LocalDate today = time.today();
    return new MembershipReport(
        from,
        to,
        groupBy,
        queries.totals(start, end, today),
        queries.byPeriod(groupBy, from, to, start, end, LibraryTime.ZONE.getId()),
        queries.byType(start, end, today));
  }
}
