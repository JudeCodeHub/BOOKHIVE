package lk.bookhive.hold.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.hold.domain.Hold;
import lk.bookhive.hold.domain.HoldStatus;
import lk.bookhive.hold.event.HoldExpired;
import lk.bookhive.hold.event.HoldExpiring;
import lk.bookhive.hold.repository.HoldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Nightly hold housekeeping (HOLD-4, steps 255 and 259):
 *
 * <ol>
 *   <li>READY holds past their pickup day expire, and each copy goes to the next in line (or back
 *       to the shelf). One transaction per hold: a problem with one doesn't stop the rest, and the
 *       copy is locked like at the desk.
 *   <li>Holds whose last pickup day is N days away (settings) get a {@link HoldExpiring} reminder;
 *       on day 0 that's "today is the last day".
 * </ol>
 *
 * Safe to run again: an expired hold isn't READY any more, and the reminder date moves on.
 */
@Service
public class HoldExpiryService {

  private static final Logger log = LoggerFactory.getLogger(HoldExpiryService.class);

  private final HoldRepository holds;
  private final HoldAssignmentService assignment;
  private final LendingCatalogService catalog;
  private final SettingsService settings;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate tx;
  private final LibraryTime time;

  public HoldExpiryService(
      HoldRepository holds,
      HoldAssignmentService assignment,
      LendingCatalogService catalog,
      SettingsService settings,
      ApplicationEventPublisher events,
      TransactionTemplate tx,
      LibraryTime time) {
    this.holds = holds;
    this.assignment = assignment;
    this.catalog = catalog;
    this.settings = settings;
    this.events = events;
    this.tx = tx;
    this.time = time;
  }

  /**
   * @return how many holds expired
   */
  public int expireOverdue() {
    LocalDate today = time.today();
    List<UUID> due =
        holds.findByStatusAndExpiresAtBefore(HoldStatus.READY, today).stream()
            .map(Hold::getId)
            .toList();
    int expired = 0;
    for (UUID holdId : due) {
      try {
        Boolean done = tx.execute(status -> expireOne(holdId, today));
        if (Boolean.TRUE.equals(done)) {
          expired++;
        }
      } catch (RuntimeException e) {
        log.warn("Hold {} could not be expired; will retry next run", holdId, e);
      }
    }
    return expired;
  }

  private boolean expireOne(UUID holdId, LocalDate today) {
    Hold first = holds.findById(holdId).orElse(null);
    if (first == null || !first.isExpiredOn(today)) {
      return false;
    }
    // Lock the copy, then read the hold again: the holder may have just borrowed it at the desk
    catalog.lockForLending(first.getAssignedCopyId());
    Hold hold = holds.findById(holdId).orElseThrow();
    if (!hold.isExpiredOn(today)) {
      return false;
    }
    Instant now = time.now();
    UUID copyId = hold.expire(now);
    holds.flush();
    String title = catalog.byId(copyId).map(c -> c.title()).orElse("");
    events.publishEvent(new HoldExpired(hold.getId(), hold.getMemberId(), hold.getBookId(), title));
    assignment.passOn(copyId, hold.getBookId(), "Hold not picked up", null, now);
    return true;
  }

  /**
   * @return how many reminders were published
   */
  public int remindExpiring() {
    LocalDate lastDay = time.today().plusDays(settings.notifications().holdExpiringDaysBefore());
    List<Hold> expiring = holds.findByStatusAndExpiresAt(HoldStatus.READY, lastDay);
    tx.executeWithoutResult(
        status ->
            expiring.forEach(
                hold ->
                    events.publishEvent(
                        new HoldExpiring(
                            hold.getId(),
                            hold.getMemberId(),
                            hold.getBookId(),
                            catalog.byId(hold.getAssignedCopyId()).map(c -> c.title()).orElse(""),
                            hold.getExpiresAt()))));
    return expiring.size();
  }
}
