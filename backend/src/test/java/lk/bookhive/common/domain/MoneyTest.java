package lk.bookhive.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void decimalsAreExactUnlikeDouble() {
    assertThat(0.1 + 0.2).isNotEqualTo(0.3);
    assertThat(Money.of("0.10").plus(Money.of("0.20"))).isEqualTo(Money.of("0.30"));
  }

  @Test
  void alwaysTwoDecimalsRoundedHalfUp() {
    assertThat(Money.ofRupees(5).amount()).hasToString("5.00");
    assertThat(Money.of("10.005").amount()).hasToString("10.01");
    assertThat(Money.of("10").equals(Money.of("10.00"))).isTrue();
  }

  @Test
  void overdueFineIsCappedPerItem() {
    Money daily = Money.ofRupees(5);
    Money cap = Money.ofRupees(300);

    assertThat(daily.times(10).min(cap)).isEqualTo(Money.ofRupees(50));
    assertThat(daily.times(90).min(cap)).isEqualTo(cap);
  }

  @Test
  void blockThresholdComparison() {
    Money threshold = Money.ofRupees(500);

    assertThat(Money.of("499.99").isAtLeast(threshold)).isFalse();
    assertThat(Money.ofRupees(500).isAtLeast(threshold)).isTrue();
    assertThat(Money.ZERO.minus(Money.ofRupees(1)).isNegative()).isTrue();
  }

  @Test
  void printsWithCurrency() {
    assertThat(Money.of("1500.5")).hasToString("LKR 1500.50");
  }
}
