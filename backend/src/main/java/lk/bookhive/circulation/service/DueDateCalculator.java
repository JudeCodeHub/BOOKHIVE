package lk.bookhive.circulation.service;

import java.time.LocalDate;
import java.util.function.Predicate;
import lk.bookhive.admin.service.LibraryHoursService;
import org.springframework.stereotype.Component;

/**
 * Due dates (CIRC-5, checklist step 219): the loan days of the member's type from today, moved to
 * the next day the library is open if that's a closed day or a holiday (PRD §4.2). Renewals use the
 * same rule, counted from the day of renewal.
 */
@Component
public class DueDateCalculator {

  /** A due date never moves further than this looking for an open day (a long closure). */
  static final int MAX_SHIFT_DAYS = 31;

  private final LibraryHoursService hours;

  public DueDateCalculator(LibraryHoursService hours) {
    this.hours = hours;
  }

  public LocalDate dueDate(LocalDate from, int loanDays) {
    return hours.nextOpenDay(from.plusDays(loanDays));
  }

  /**
   * The rule itself, without the database: easy to test with any calendar.
   *
   * @param isOpen whether the library is open on a date
   */
  public static LocalDate dueDate(LocalDate from, int loanDays, Predicate<LocalDate> isOpen) {
    if (loanDays < 1) {
      throw new IllegalArgumentException("loanDays must be at least 1");
    }
    LocalDate due = from.plusDays(loanDays);
    for (int i = 0; i < MAX_SHIFT_DAYS && !isOpen.test(due); i++) {
      due = due.plusDays(1);
    }
    return due;
  }
}
