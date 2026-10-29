package lk.bookhive.report.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.report.service.DashboardService;
import lk.bookhive.report.service.DashboardService.Dashboard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Today's numbers for the staff home page (RPT-1, checklist step 360); librarians and admins. */
@RestController
@RequestMapping("/staff/dashboard")
@Tag(name = "Staff: dashboard")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffDashboardController {

  /**
   * @param checkedOut loans made today
   * @param returned books back today
   * @param dueToday still out, due back today
   * @param overdue still out, past their due date
   */
  public record DeskSummary(long checkedOut, long returned, long dueToday, long overdue) {}

  /**
   * @param ready on the hold shelf, waiting to be picked up
   * @param expiringToday of those, today is the last pickup day
   * @param waiting members queueing for a copy
   */
  public record HoldShelfCounts(long ready, long expiringToday, long waiting) {}

  public record ToDoSummary(long applications, long reviews, long suggestions) {}

  /** Amounts in LKR. */
  public record FineDaySummary(BigDecimal collected, BigDecimal waived, BigDecimal outstanding) {}

  public record DayActivitySummary(LocalDate day, long checkouts, long returns) {}

  /**
   * @param today the library's date (Sri Lanka)
   * @param lastTwoWeeks oldest first, today last; days without activity are 0
   */
  public record StaffDashboardResponse(
      LocalDate today,
      DeskSummary desk,
      HoldShelfCounts holds,
      ToDoSummary toDo,
      FineDaySummary fines,
      long eventsToday,
      List<DayActivitySummary> lastTwoWeeks) {

    static StaffDashboardResponse from(Dashboard d) {
      return new StaffDashboardResponse(
          d.today(),
          new DeskSummary(
              d.desk().checkedOut(), d.desk().returned(), d.desk().dueToday(), d.desk().overdue()),
          new HoldShelfCounts(d.holds().ready(), d.holds().expiringToday(), d.holds().waiting()),
          new ToDoSummary(d.toDo().applications(), d.toDo().reviews(), d.toDo().suggestions()),
          new FineDaySummary(d.fines().collected(), d.fines().waived(), d.fines().outstanding()),
          d.eventsToday(),
          d.lastTwoWeeks().stream()
              .map(a -> new DayActivitySummary(a.day(), a.checkouts(), a.returns()))
              .toList());
    }
  }

  private final DashboardService dashboard;

  public StaffDashboardController(DashboardService dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping
  @Operation(
      summary = "Today's numbers: desk, hold shelf, things to do, fines, events, last 14 days")
  public StaffDashboardResponse today() {
    return StaffDashboardResponse.from(dashboard.today());
  }
}
