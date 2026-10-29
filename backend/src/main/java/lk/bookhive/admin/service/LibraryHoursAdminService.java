package lk.bookhive.admin.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lk.bookhive.admin.domain.Holiday;
import lk.bookhive.admin.domain.OpeningHours;
import lk.bookhive.admin.event.ClosedDaysChanged;
import lk.bookhive.admin.repository.HolidayRepository;
import lk.bookhive.admin.repository.OpeningHoursRepository;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins change the weekly opening hours and the holidays (ADM-3, checklist step 357). When days
 * become closed, loans and holds due on them move to the next open day (ClosedDaysChanged), so
 * nobody is fined for a day the library was shut. Every change is audited.
 */
@Service
public class LibraryHoursAdminService {

  /** Hours for one weekday; both times null = closed. */
  public record DayHours(DayOfWeek day, LocalTime opensAt, LocalTime closesAt) {}

  /** A holiday's date and its name in each language. */
  public record HolidayDetails(LocalDate date, String nameEn, String nameSi, String nameTa) {}

  private final OpeningHoursRepository hours;
  private final HolidayRepository holidays;
  private final AuditService audit;
  private final ApplicationEventPublisher events;
  private final LibraryTime time;

  public LibraryHoursAdminService(
      OpeningHoursRepository hours,
      HolidayRepository holidays,
      AuditService audit,
      ApplicationEventPublisher events,
      LibraryTime time) {
    this.hours = hours;
    this.holidays = holidays;
    this.audit = audit;
    this.events = events;
    this.time = time;
  }

  /**
   * Replaces the whole week: all seven days, each closed or open with opening before closing, and
   * the library open on at least one day.
   */
  @Transactional
  public List<OpeningHours> updateWeek(List<DayHours> week, UUID adminId) {
    Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
    for (DayHours day : week) {
      if (day.day() == null || !seen.add(day.day())) {
        throw new FieldValidationException(
            "week", "WEEK_INCOMPLETE", "Give each day of the week exactly once.");
      }
      boolean closed = day.opensAt() == null && day.closesAt() == null;
      boolean open =
          day.opensAt() != null && day.closesAt() != null && day.opensAt().isBefore(day.closesAt());
      if (!closed && !open) {
        throw new FieldValidationException(
            "week." + day.day(), "HOURS_INVALID", "Opening time must be before closing time.");
      }
    }
    if (seen.size() != 7) {
      throw new FieldValidationException(
          "week", "WEEK_INCOMPLETE", "Give each day of the week exactly once.");
    }
    if (week.stream().allMatch(d -> d.opensAt() == null)) {
      throw new FieldValidationException(
          "week", "NEVER_OPEN", "The library must be open on at least one day.");
    }

    Map<DayOfWeek, OpeningHours> rows = new LinkedHashMap<>();
    hours.findAllByOrderByDayOfWeek().forEach(row -> rows.put(row.getDay(), row));
    Map<String, Object> changed = new LinkedHashMap<>();
    for (DayHours day : week) {
      OpeningHours row = rows.get(day.day());
      if (row == null) {
        throw new IllegalStateException("opening_hours row for " + day.day() + " is missing");
      }
      if (!Objects.equals(row.getOpensAt(), day.opensAt())
          || !Objects.equals(row.getClosesAt(), day.closesAt())) {
        changed.put(
            day.day().name(),
            day.opensAt() == null ? "closed" : day.opensAt() + "-" + day.closesAt());
        row.change(day.opensAt(), day.closesAt());
      }
    }
    if (!changed.isEmpty()) {
      hours.flush();
      audit.record("library.hours_updated", adminId, "opening_hours", "week", changed);
      events.publishEvent(new ClosedDaysChanged(time.today()));
    }
    return hours.findAllByOrderByDayOfWeek();
  }

  /** One year's holidays, in date order. */
  @Transactional(readOnly = true)
  public List<Holiday> holidays(int year) {
    return holidays.findByDateBetweenOrderByDate(
        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
  }

  @Transactional
  public Holiday addHoliday(HolidayDetails details, UUID adminId) {
    checkDate(details.date(), null);
    Holiday holiday =
        holidays.saveAndFlush(
            Holiday.of(details.date(), details.nameEn(), details.nameSi(), details.nameTa()));
    audit.record(
        "library.holiday_added",
        adminId,
        "holiday",
        holiday.getId(),
        Map.of("date", details.date().toString(), "name", details.nameEn()));
    events.publishEvent(new ClosedDaysChanged(details.date()));
    return holiday;
  }

  @Transactional
  public Holiday updateHoliday(UUID id, HolidayDetails details, UUID adminId) {
    Holiday holiday = require(id);
    LocalDate oldDate = holiday.getDate();
    if (!details.date().equals(oldDate)) {
      checkDate(details.date(), id);
    }
    holiday.change(details.date(), details.nameEn(), details.nameSi(), details.nameTa());
    holidays.flush();
    audit.record(
        "library.holiday_updated",
        adminId,
        "holiday",
        id,
        Map.of(
            "from", oldDate.toString(), "to", details.date().toString(), "name", details.nameEn()));
    if (!details.date().equals(oldDate)) {
      events.publishEvent(new ClosedDaysChanged(details.date()));
    }
    return holiday;
  }

  /** The library opens on that day after all (loans already moved stay later: no harm). */
  @Transactional
  public void deleteHoliday(UUID id, UUID adminId) {
    Holiday holiday = require(id);
    holidays.delete(holiday);
    audit.record(
        "library.holiday_deleted",
        adminId,
        "holiday",
        id,
        Map.of("date", holiday.getDate().toString(), "name", holiday.getName(Language.EN)));
  }

  /** Not in the past (it would change nothing), and only one holiday per day. */
  private void checkDate(LocalDate date, UUID self) {
    if (date.isBefore(time.today())) {
      throw new FieldValidationException(
          "date", "HOLIDAY_IN_PAST", "Choose today or a later date.");
    }
    boolean taken =
        holidays.findByDateBetweenOrderByDate(date, date).stream()
            .anyMatch(h -> !h.getId().equals(self));
    if (taken) {
      throw new FieldValidationException(
          "date", "HOLIDAY_DATE_TAKEN", "There's already a holiday on this day.");
    }
  }

  private Holiday require(UUID id) {
    return holidays
        .findById(id)
        .orElseThrow(() -> new NotFoundException("HOLIDAY_NOT_FOUND", "Holiday not found."));
  }
}
