package lk.bookhive.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LibraryTimeTest {

  @Test
  void todayUsesColomboDateNotUtcDate() {
    // 19:00 UTC on the 21st is 00:30 on the 22nd in Colombo (UTC+5:30)
    Clock clock = Clock.fixed(Instant.parse("2026-10-21T19:00:00Z"), ZoneOffset.UTC);

    LibraryTime time = new LibraryTime(clock);

    assertThat(time.today()).isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 10, 21));
  }

  @Test
  void convertsTimestampToLibraryDate() {
    LibraryTime time = new LibraryTime(Clock.systemUTC());

    assertThat(time.toLibraryDate(Instant.parse("2026-10-21T18:29:59Z")))
        .isEqualTo(LocalDate.of(2026, 10, 21));
    assertThat(time.toLibraryDate(Instant.parse("2026-10-21T18:30:00Z")))
        .isEqualTo(LocalDate.of(2026, 10, 22));
  }
}
