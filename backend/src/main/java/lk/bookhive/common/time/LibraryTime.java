package lk.bookhive.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Two kinds of time:
 *
 * <ul>
 *   <li><b>Timestamps</b> ({@link Instant}, "when did it happen") are stored in UTC.
 *   <li><b>Business dates</b> ({@link LocalDate}: due dates, fine days, holidays) are calendar
 *       days in Sri Lanka, where the library is.
 * </ul>
 *
 * <p>At 2:00 AM in Colombo it's still the previous day in UTC, so "today" must always come from
 * {@link #today()}, never from {@code LocalDate.now()}.
 */
@Component
public class LibraryTime {

  public static final ZoneId ZONE = ZoneId.of("Asia/Colombo");

  private final Clock clock;

  public LibraryTime(Clock clock) {
    this.clock = clock;
  }

  public Instant now() {
    return Instant.now(clock);
  }

  /** Today's date in the library's time zone. */
  public LocalDate today() {
    return LocalDate.now(clock.withZone(ZONE));
  }

  /** The library calendar date of a stored timestamp, e.g. the day a book was checked out. */
  public LocalDate toLibraryDate(Instant instant) {
    return LocalDate.ofInstant(instant, ZONE);
  }
}
