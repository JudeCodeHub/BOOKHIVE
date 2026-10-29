package lk.bookhive.report.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.bookhive.catalog.service.PurchaseSuggestionService;
import lk.bookhive.circulation.service.LoanLookupService;
import lk.bookhive.circulation.service.LoanLookupService.DayActivity;
import lk.bookhive.circulation.service.LoanLookupService.DeskDay;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.community.service.ReviewService;
import lk.bookhive.event.service.EventQueryService;
import lk.bookhive.fine.service.FineQueryService;
import lk.bookhive.fine.service.FineQueryService.MoneyTotals;
import lk.bookhive.hold.service.HoldListService;
import lk.bookhive.hold.service.HoldListService.ShelfCounts;
import lk.bookhive.member.service.ApplicationReviewService;
import org.springframework.stereotype.Service;

/**
 * The staff dashboard (RPT-1, checklist step 360): today's numbers from every module, in library
 * time (Sri Lanka), plus the last two weeks of loans and returns for the chart. Each module counts
 * its own data through its service; this only puts them together. Small count queries, so no cache:
 * the numbers are always current.
 */
@Service
public class DashboardService {

  /** Days in the chart, today included. */
  static final int TREND_DAYS = 14;

  /**
   * Things waiting for staff.
   *
   * @param applications membership applications to decide
   * @param reviews member reviews to moderate
   * @param suggestions purchase suggestions nobody has looked at yet
   */
  public record ToDo(long applications, long reviews, long suggestions) {}

  public record Dashboard(
      LocalDate today,
      DeskDay desk,
      ShelfCounts holds,
      ToDo toDo,
      MoneyTotals fines,
      long eventsToday,
      List<DayActivity> lastTwoWeeks) {}

  private final LoanLookupService loans;
  private final HoldListService holds;
  private final ApplicationReviewService applications;
  private final ReviewService reviews;
  private final PurchaseSuggestionService suggestions;
  private final FineQueryService fines;
  private final EventQueryService events;
  private final LibraryTime time;

  public DashboardService(
      LoanLookupService loans,
      HoldListService holds,
      ApplicationReviewService applications,
      ReviewService reviews,
      PurchaseSuggestionService suggestions,
      FineQueryService fines,
      EventQueryService events,
      LibraryTime time) {
    this.loans = loans;
    this.holds = holds;
    this.applications = applications;
    this.reviews = reviews;
    this.suggestions = suggestions;
    this.fines = fines;
    this.events = events;
    this.time = time;
  }

  public Dashboard today() {
    LocalDate today = time.today();
    Instant start = today.atStartOfDay(LibraryTime.ZONE).toInstant();
    Instant end = today.plusDays(1).atStartOfDay(LibraryTime.ZONE).toInstant();
    return new Dashboard(
        today,
        loans.deskDay(start, end, today),
        holds.shelfCounts(),
        new ToDo(applications.pendingCount(), reviews.pendingCount(), suggestions.newCount()),
        fines.moneyTotals(start, end),
        events.countStarting(start, end),
        loans.dailyActivity(today.minusDays(TREND_DAYS - 1), today));
  }
}
