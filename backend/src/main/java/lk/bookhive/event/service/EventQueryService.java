package lk.bookhive.event.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.event.domain.EventStatus;
import lk.bookhive.event.domain.LibraryEvent;
import lk.bookhive.event.domain.RegistrationStatus;
import lk.bookhive.event.repository.EventRegistrationRepository;
import lk.bookhive.event.repository.LibraryEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Events for the public website (steps 332, 341-342): published (and cancelled) ones only. */
@Service
public class EventQueryService {

  /**
   * @param placesLeft null when there's no limit
   * @param waitlisted members waiting for a place
   */
  public record PublicEvent(LibraryEvent event, Long placesLeft, long waitlisted) {}

  private final LibraryEventRepository events;
  private final EventRegistrationRepository registrations;
  private final EventRegistrationService signUps;
  private final LibraryTime time;

  public EventQueryService(
      LibraryEventRepository events,
      EventRegistrationRepository registrations,
      EventRegistrationService signUps,
      LibraryTime time) {
    this.events = events;
    this.registrations = registrations;
    this.signUps = signUps;
    this.time = time;
  }

  /** Upcoming and ongoing published events, soonest first. */
  @Transactional(readOnly = true)
  public Page<PublicEvent> upcoming(int page, int size) {
    return events
        .findByStatusAndEndsAtAfterOrderByStartsAt(
            EventStatus.PUBLISHED, time.now(), PageRequest.of(page, size))
        .map(this::view);
  }

  /** Published events starting in [from, to) (calendar month). */
  @Transactional(readOnly = true)
  public List<PublicEvent> between(Instant from, Instant to) {
    return events
        .findByStatusAndStartsAtBetweenOrderByStartsAt(EventStatus.PUBLISHED, from, to)
        .stream()
        .map(this::view)
        .toList();
  }

  /** One event; drafts don't exist for the public, cancelled ones are shown as cancelled. */
  @Transactional(readOnly = true)
  public PublicEvent get(UUID id) {
    LibraryEvent event =
        events
            .findById(id)
            .filter(e -> e.getStatus() != EventStatus.DRAFT)
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Event not found."));
    return view(event);
  }

  private PublicEvent view(LibraryEvent event) {
    Long left =
        event.hasLimitedPlaces()
            ? Math.max(0, event.getCapacity() - signUps.placesTaken(event.getId()))
            : null;
    long waiting =
        registrations.countByEventIdAndStatusIn(
            event.getId(), List.of(RegistrationStatus.WAITLISTED));
    return new PublicEvent(event, left, waiting);
  }
}
