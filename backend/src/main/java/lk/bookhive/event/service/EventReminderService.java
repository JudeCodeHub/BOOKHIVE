package lk.bookhive.event.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.event.domain.EventRegistration;
import lk.bookhive.event.domain.EventStatus;
import lk.bookhive.event.domain.LibraryEvent;
import lk.bookhive.event.domain.RegistrationStatus;
import lk.bookhive.event.event.EventReminderDue;
import lk.bookhive.event.repository.EventRegistrationRepository;
import lk.bookhive.event.repository.LibraryEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Day-before event reminders (step 333): everyone with a place at a published event that starts
 * tomorrow (library date) gets a {@link EventReminderDue}. Running it twice sends nothing new (the
 * notifications are deduplicated per registration).
 */
@Service
public class EventReminderService {

  private final LibraryEventRepository events;
  private final EventRegistrationRepository registrations;
  private final ApplicationEventPublisher publisher;
  private final LibraryTime time;

  public EventReminderService(
      LibraryEventRepository events,
      EventRegistrationRepository registrations,
      ApplicationEventPublisher publisher,
      LibraryTime time) {
    this.events = events;
    this.registrations = registrations;
    this.publisher = publisher;
    this.time = time;
  }

  /**
   * @return how many members were reminded
   */
  @Transactional(readOnly = true)
  public int remindTomorrow() {
    LocalDate tomorrow = time.today().plusDays(1);
    Instant from = tomorrow.atStartOfDay(LibraryTime.ZONE).toInstant();
    Instant to = tomorrow.plusDays(1).atStartOfDay(LibraryTime.ZONE).toInstant();
    Map<UUID, LibraryEvent> byId =
        events
            .findByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                EventStatus.PUBLISHED, from, to)
            .stream()
            .collect(Collectors.toMap(LibraryEvent::getId, Function.identity()));
    if (byId.isEmpty()) {
      return 0;
    }
    List<EventRegistration> going =
        registrations.findByEventIdInAndStatus(byId.keySet(), RegistrationStatus.REGISTERED);
    for (EventRegistration registration : going) {
      LibraryEvent event = byId.get(registration.getEventId());
      publisher.publishEvent(
          new EventReminderDue(
              registration.getId(),
              event.getId(),
              registration.getMemberId(),
              event.getTitle(),
              event.getStartsAt(),
              event.getVenue()));
    }
    return going.size();
  }
}
