package lk.bookhive.admin.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.bookhive.admin.domain.Holiday;
import lk.bookhive.admin.domain.OpeningHours;
import lk.bookhive.admin.repository.HolidayRepository;
import lk.bookhive.admin.repository.OpeningHoursRepository;
import lk.bookhive.common.time.LibraryTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * When the library is open (ADM-3, checklist step 205), always in Sri Lankan time (LibraryTime):
 * the weekly hours minus holidays. Also what circulation needs later: "is this an open day?" and
 * "the next open day" for due dates (CIRC-5).
 */
@Service
public class LibraryHoursService {

  /** How far ahead to look for the next opening (long holiday seasons included). */
  private static final int LOOKAHEAD_DAYS = 31;

  private final OpeningHoursRepository hours;
  private final HolidayRepository holidays;
  private final LibraryTime time;

  public LibraryHoursService(
      OpeningHoursRepository hours, HolidayRepository holidays, LibraryTime time) {
    this.hours = hours;
    this.holidays = holidays;
    this.time = time;
  }

  /**
   * Right now, for the "Open now" badge.
   *
   * @param closesAt when today's opening ends (only while open)
   * @param nextOpening the next time the doors open (when closed); null if none within a month
   * @param holiday today's holiday, if today is one
   */
  public record Status(
      boolean openNow, LocalTime closesAt, LocalDateTime nextOpening, Holiday holiday) {}

  @Transactional(readOnly = true)
  public List<OpeningHours> week() {
    return hours.findAllByOrderByDayOfWeek();
  }

  /** Holidays from today, soonest first. */
  @Transactional(readOnly = true)
  public List<Holiday> upcomingHolidays(int days) {
    LocalDate today = time.today();
    return holidays.findByDateBetweenOrderByDate(today, today.plusDays(days));
  }

  @Transactional(readOnly = true)
  public Status status() {
    LocalDateTime now = LocalDateTime.ofInstant(time.now(), LibraryTime.ZONE);
    LocalDate today = now.toLocalDate();
    Map<DayOfWeek, OpeningHours> byDay = byDay();
    Map<LocalDate, Holiday> closed = holidaysFrom(today);

    Holiday todayHoliday = closed.get(today);
    OpeningHours todayHours = byDay.get(today.getDayOfWeek());
    if (todayHoliday == null && todayHours != null && todayHours.isOpenAt(now.toLocalTime())) {
      return new Status(true, todayHours.getClosesAt(), null, null);
    }
    // Closed now: later today (before opening time), or the next open day
    for (int i = 0; i <= LOOKAHEAD_DAYS; i++) {
      LocalDate day = today.plusDays(i);
      OpeningHours dayHours = byDay.get(day.getDayOfWeek());
      if (closed.containsKey(day) || dayHours == null || dayHours.isClosed()) {
        continue;
      }
      LocalDateTime opens = day.atTime(dayHours.getOpensAt());
      if (opens.isAfter(now)) {
        return new Status(false, null, opens, todayHoliday);
      }
    }
    return new Status(false, null, null, todayHoliday);
  }

  /** True on a normal open weekday that isn't a holiday (for due dates, CIRC-5). */
  @Transactional(readOnly = true)
  public boolean isOpenOn(LocalDate date) {
    OpeningHours dayHours = byDay().get(date.getDayOfWeek());
    return dayHours != null
        && !dayHours.isClosed()
        && holidays.findByDateBetweenOrderByDate(date, date).isEmpty();
  }

  /** {@code date} if the library is open then, otherwise the next open day. */
  @Transactional(readOnly = true)
  public LocalDate nextOpenDay(LocalDate date) {
    Map<DayOfWeek, OpeningHours> byDay = byDay();
    Map<LocalDate, Holiday> closed = holidaysFrom(date);
    for (int i = 0; i <= LOOKAHEAD_DAYS; i++) {
      LocalDate day = date.plusDays(i);
      OpeningHours dayHours = byDay.get(day.getDayOfWeek());
      if (!closed.containsKey(day) && dayHours != null && !dayHours.isClosed()) {
        return day;
      }
    }
    // No open day for a month (shouldn't happen): don't move the date at all
    return date;
  }

  private Map<DayOfWeek, OpeningHours> byDay() {
    return hours.findAll().stream()
        .collect(Collectors.toMap(OpeningHours::getDay, Function.identity()));
  }

  private Map<LocalDate, Holiday> holidaysFrom(LocalDate from) {
    return holidays.findByDateBetweenOrderByDate(from, from.plusDays(LOOKAHEAD_DAYS)).stream()
        .collect(Collectors.toMap(Holiday::getDate, Function.identity()));
  }
}
