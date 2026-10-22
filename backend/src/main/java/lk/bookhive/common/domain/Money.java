package lk.bookhive.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in Sri Lankan rupees, always with 2 decimals (cents). Uses {@link BigDecimal} because
 * {@code double} can't store most decimals exactly ({@code 0.1 + 0.2 = 0.30000000000000004}). There
 * is deliberately no {@code of(double)}: create amounts from a String or whole rupees.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

  public static final Currency LKR = Currency.getInstance("LKR");
  public static final int SCALE = 2;
  public static final Money ZERO = new Money(BigDecimal.ZERO);

  public Money {
    Objects.requireNonNull(amount, "amount");
    amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
  }

  /** {@code Money.of("1500.50")} */
  public static Money of(String amount) {
    return new Money(new BigDecimal(amount));
  }

  /** {@code Money.ofRupees(300)} */
  public static Money ofRupees(long rupees) {
    return new Money(BigDecimal.valueOf(rupees));
  }

  public Money plus(Money other) {
    return new Money(amount.add(other.amount));
  }

  public Money minus(Money other) {
    return new Money(amount.subtract(other.amount));
  }

  public Money times(long factor) {
    return new Money(amount.multiply(BigDecimal.valueOf(factor)));
  }

  /** The smaller amount, e.g. {@code dailyFine.times(days).min(cap)}. */
  public Money min(Money other) {
    return compareTo(other) <= 0 ? this : other;
  }

  public boolean isZero() {
    return amount.signum() == 0;
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  public boolean isAtLeast(Money other) {
    return compareTo(other) >= 0;
  }

  @Override
  public int compareTo(Money other) {
    return amount.compareTo(other.amount);
  }

  @Override
  public String toString() {
    return "LKR " + amount.toPlainString();
  }
}
