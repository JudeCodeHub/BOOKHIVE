package lk.bookhive.circulation.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lk.bookhive.admin.settings.Settings.Fines;
import lk.bookhive.common.domain.Money;

/**
 * The overdue fine for one item (PRD §4.4, FIN-1, checklist steps 223 and 270), from the admin
 * settings: LKR 5 a day by default, after 1 grace day, at most LKR 300 per item.
 *
 * <p>The grace days are free: with 1 grace day, an item 1 day late costs nothing and an item 3 days
 * late is charged for 2 days. Calendar days (the library being closed doesn't stop the clock),
 * never more than the per-item maximum.
 *
 * <p>Pure functions, the single source of the rule: the returns desk (what's owed on return) and
 * the fine module (daily accrual, step 271) both use it, so they can never disagree. It lives with
 * circulation because the fine module already depends on circulation, not the other way round.
 */
public final class FineCalculator {

  private FineCalculator() {}

  /**
   * @param daysLate calendar days after the due date
   * @param chargedDays of those, the ones that cost money (after the grace days)
   * @param amount the fine, capped at the per-item maximum
   */
  public record LateFine(long daysLate, long chargedDays, Money amount) {

    public boolean any() {
      return !amount.isZero();
    }
  }

  /**
   * The whole fine for an item due on {@code dueDate} and returned (or still out) on {@code asOf}.
   */
  public static LateFine lateFine(LocalDate dueDate, LocalDate asOf, Fines rules) {
    long daysLate = Math.max(0, ChronoUnit.DAYS.between(dueDate, asOf));
    long charged = Math.max(0, daysLate - rules.graceDays());
    Money amount = new Money(rules.perDay()).times(charged).min(new Money(rules.maxPerItem()));
    return new LateFine(daysLate, charged, amount);
  }

  /**
   * How much more to charge now, given what's already on the ledger (daily accrual, step 271): the
   * fine as of {@code asOf} minus what was charged before. Never negative: if the rate was lowered
   * since, nothing already charged is taken back automatically (staff can waive). Running it twice
   * on the same day adds nothing the second time.
   */
  public static Money stillToCharge(
      Money alreadyCharged, LocalDate dueDate, LocalDate asOf, Fines rules) {
    Money target = lateFine(dueDate, asOf, rules).amount();
    Money missing = target.minus(alreadyCharged);
    return missing.isNegative() ? Money.ZERO : missing;
  }
}
