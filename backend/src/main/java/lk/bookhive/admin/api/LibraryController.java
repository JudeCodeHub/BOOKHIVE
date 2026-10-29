package lk.bookhive.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lk.bookhive.admin.config.LibraryProperties;
import lk.bookhive.admin.domain.Holiday;
import lk.bookhive.admin.domain.OpeningHours;
import lk.bookhive.admin.service.LibraryHoursService;
import lk.bookhive.admin.service.LibraryHoursService.Status;
import lk.bookhive.admin.settings.Settings.Fines;
import lk.bookhive.admin.settings.Settings.Membership;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.common.domain.Language;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public library information: hours, holidays, "open now", contact (PRD §9 "Public info"). */
@RestController
@RequestMapping("/library")
@Tag(name = "Library")
@EnableConfigurationProperties(LibraryProperties.class)
public class LibraryController {

  private static final int HOLIDAYS_AHEAD_DAYS = 60;

  private final LibraryHoursService hours;
  private final LibraryProperties library;
  private final SettingsService settings;

  public LibraryController(
      LibraryHoursService hours, LibraryProperties library, SettingsService settings) {
    this.hours = hours;
    this.library = library;
    this.settings = settings;
  }

  /** The library-wide rules; per-type limits come from GET /memberships/types. Amounts in LKR. */
  public record RulesResponse(
      BigDecimal finePerDay,
      int fineGraceDays,
      BigDecimal fineMaxPerItem,
      BigDecimal lostDefaultPrice,
      BigDecimal lostProcessingFee,
      BigDecimal blockBorrowingAt,
      int holdPickupOpenDays,
      int membershipValidityYears,
      int documentRetentionDays,
      int maxItemsPerCheckout) {}

  @GetMapping("/rules")
  @Operation(summary = "Borrowing rules: fines, lost items, holds, membership, checkout size")
  public RulesResponse rules() {
    // From the admin-editable settings, so the page says exactly what the system enforces
    Fines fines = settings.fines();
    Membership membership = settings.membership();
    return new RulesResponse(
        fines.perDay(),
        fines.graceDays(),
        fines.maxPerItem(),
        fines.lostDefaultPrice(),
        fines.lostProcessingFee(),
        fines.blockBorrowingAt(),
        settings.holds().pickupOpenDays(),
        membership.validityYears(),
        membership.documentRetentionDays(),
        settings.loans().maxItemsPerCheckout());
  }

  /** One weekday. Both times null = closed. */
  public record DaySummary(DayOfWeek day, LocalTime opensAt, LocalTime closesAt) {

    static DaySummary from(OpeningHours h) {
      return new DaySummary(h.getDay(), h.getOpensAt(), h.getClosesAt());
    }
  }

  public record HolidaySummary(LocalDate date, String nameEn, String nameSi, String nameTa) {

    static HolidaySummary from(Holiday h) {
      return new HolidaySummary(
          h.getDate(), h.getName(Language.EN), h.getName(Language.SI), h.getName(Language.TA));
    }
  }

  /**
   * Times are Sri Lankan local time.
   *
   * @param closesAt while open: when it closes today
   * @param nextOpening while closed: when it opens next (null if not within a month)
   * @param todayHoliday today's holiday, if the library is closed for one
   */
  public record HoursResponse(
      boolean openNow,
      LocalTime closesAt,
      LocalDateTime nextOpening,
      HolidaySummary todayHoliday,
      List<DaySummary> week,
      List<HolidaySummary> upcomingHolidays) {}

  public record LibraryInfoResponse(
      String name,
      String addressLine,
      String city,
      String postalCode,
      String phone,
      String email,
      double latitude,
      double longitude) {}

  @GetMapping("/hours")
  @Operation(summary = "Opening hours, upcoming holidays and whether the library is open now")
  public HoursResponse hours() {
    Status status = hours.status();
    return new HoursResponse(
        status.openNow(),
        status.closesAt(),
        status.nextOpening(),
        status.holiday() == null ? null : HolidaySummary.from(status.holiday()),
        hours.week().stream().map(DaySummary::from).toList(),
        hours.upcomingHolidays(HOLIDAYS_AHEAD_DAYS).stream().map(HolidaySummary::from).toList());
  }

  @GetMapping("/info")
  @Operation(summary = "Address, phone, email and map location")
  public LibraryInfoResponse info() {
    return new LibraryInfoResponse(
        library.name(),
        library.addressLine(),
        library.city(),
        library.postalCode(),
        library.phone(),
        library.email(),
        library.latitude(),
        library.longitude());
  }
}
