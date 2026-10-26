package lk.bookhive.hold.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.admin.service.LibraryHoursService;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.hold.domain.Hold;
import lk.bookhive.hold.domain.HoldStatus;
import lk.bookhive.hold.event.HoldReady;
import lk.bookhive.hold.repository.HoldRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giving a copy to the queue (HOLD-3, steps 253-256). Whenever a copy of a title becomes free for
 * the queue (returned, or a READY hold expired or was cancelled), the first member in line gets it:
 * their hold becomes READY with a pickup deadline of N open days (settings, 3 by default) and
 * {@link HoldReady} is published. With nobody waiting, a copy on the hold shelf goes back to the
 * open shelf.
 *
 * <p>Runs inside the caller's transaction, which has locked the copy row.
 */
@Service
public class HoldAssignmentService {

  private final HoldRepository holds;
  private final HoldQueueService queue;
  private final LendingCatalogService catalog;
  private final LibraryHoursService hours;
  private final SettingsService settings;
  private final ApplicationEventPublisher events;
  private final LibraryTime time;

  public HoldAssignmentService(
      HoldRepository holds,
      HoldQueueService queue,
      LendingCatalogService catalog,
      LibraryHoursService hours,
      SettingsService settings,
      ApplicationEventPublisher events,
      LibraryTime time) {
    this.holds = holds;
    this.queue = queue;
    this.catalog = catalog;
    this.hours = hours;
    this.settings = settings;
    this.events = events;
    this.time = time;
  }

  /**
   * The copy is free for the queue: set it aside for the first in line.
   *
   * @return their hold, now READY; empty when nobody is waiting
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<Hold> assignToNext(UUID bookId, UUID copyId, Instant now) {
    Optional<Hold> next = queue.firstInLine(bookId);
    next.ifPresent(
        hold -> {
          LocalDate pickUpBy = pickUpBy(time.today());
          hold.makeReady(copyId, now, pickUpBy);
          // Flush now: the unique index (one READY hold per copy) checks it in this transaction
          holds.saveAndFlush(hold);
          String title = catalog.byId(copyId).map(c -> c.title()).orElse("");
          events.publishEvent(
              new HoldReady(hold.getId(), hold.getMemberId(), bookId, copyId, title, pickUpBy));
        });
    return next;
  }

  /**
   * A copy on the hold shelf is no longer set aside for its member (hold expired, cancelled, or
   * they borrowed another copy): the next in line gets it, or it goes back to the open shelf.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void passOn(UUID copyId, UUID bookId, String reason, UUID actorId, Instant now) {
    // Same row lock as checkout and returns: nobody lends this copy while it changes hands
    catalog.lockForLending(copyId);
    if (assignToNext(bookId, copyId, now).isEmpty()) {
      catalog.releaseFromHoldShelf(copyId, reason + ": back to the shelf", actorId, now);
    }
  }

  /** Is anybody still holding this copy on the shelf? */
  @Transactional(readOnly = true)
  public Optional<Hold> readyHoldFor(UUID copyId) {
    return holds.findFirstByAssignedCopyIdAndStatus(copyId, HoldStatus.READY);
  }

  /**
   * The last day to pick up: the N-th open day after today (PRD §4.3: "3 open days"), so closed
   * days and holidays don't eat into the window.
   */
  public LocalDate pickUpBy(LocalDate today) {
    LocalDate day = today;
    for (int i = 0; i < settings.holds().pickupOpenDays(); i++) {
      day = hours.nextOpenDay(day.plusDays(1));
    }
    return day;
  }
}
