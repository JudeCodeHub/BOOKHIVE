package lk.bookhive.hold.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import lk.bookhive.admin.event.ClosedDaysChanged;
import lk.bookhive.admin.service.LibraryHoursService;
import lk.bookhive.hold.domain.Hold;
import lk.bookhive.hold.domain.HoldStatus;
import lk.bookhive.hold.repository.HoldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A day became closed (step 357): a reserved book whose last pickup day is now closed waits until
 * the next open day, so the member doesn't lose it because the library was shut. Runs in the
 * admin's transaction.
 */
@Component
public class ClosedDayPickupListener {

  private static final Logger log = LoggerFactory.getLogger(ClosedDayPickupListener.class);

  private final HoldRepository holds;
  private final LibraryHoursService hours;

  public ClosedDayPickupListener(HoldRepository holds, LibraryHoursService hours) {
    this.holds = holds;
    this.hours = hours;
  }

  @EventListener
  public void onClosedDaysChanged(ClosedDaysChanged event) {
    Map<LocalDate, LocalDate> moveTo = new HashMap<>();
    int moved = 0;
    for (Hold hold :
        holds.findByStatusAndExpiresAtGreaterThanEqual(HoldStatus.READY, event.from())) {
      LocalDate target = moveTo.computeIfAbsent(hold.getExpiresAt(), hours::nextOpenDay);
      if (!target.equals(hold.getExpiresAt())) {
        hold.extendPickup(target);
        moved++;
      }
    }
    if (moved > 0) {
      log.info("Moved {} hold pickup day(s) off newly closed days", moved);
    }
  }
}
