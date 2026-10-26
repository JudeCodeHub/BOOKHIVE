package lk.bookhive.circulation.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lk.bookhive.admin.settings.Settings.Fines;
import lk.bookhive.common.domain.Money;

/**
 * The late-return fine for one item (PRD §4.4, checklist step 223), from the admin settings.
 *
 * <p>The grace days are free: with 1 grace day, an item 1 day late costs nothing and an item 3 days
 * late is charged for 2 days. Calendar days (the library being closed doesn't stop the clock),
 * never more than the per-item maximum.
 */
public final class FineCalculator {

  private FineCalculator() {}

  public record LateFine(long daysLate, long chargedDays, Money amount) {

    public boolean any() {
      return !amount.isZero();
    }
  }

  public static LateFine lateFine(LocalDate dueDate, LocalDate returnedOn, Fines rules) {
    long daysLate = Math.max(0, ChronoUnit.DAYS.between(dueDate, returnedOn));
    long charged = Math.max(0, daysLate - rules.graceDays());
    Money amount = new Money(rules.perDay()).times(charged).min(new Money(rules.maxPerItem()));
    return new LateFine(daysLate, charged, amount);
  }
}
