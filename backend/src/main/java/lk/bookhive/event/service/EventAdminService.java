package lk.bookhive.event.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.storage.FileStorage;
import lk.bookhive.common.storage.Pictures;
import lk.bookhive.common.text.RichText;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.event.domain.EventRegistration;
import lk.bookhive.event.domain.EventStatus;
import lk.bookhive.event.domain.LibraryEvent;
import lk.bookhive.event.domain.LibraryEvent.Details;
import lk.bookhive.event.domain.RegistrationStatus;
import lk.bookhive.event.event.EventCancelled;
import lk.bookhive.event.repository.EventRegistrationRepository;
import lk.bookhive.event.repository.LibraryEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Events for staff (EVT-1, checklist step 331): create as a draft, edit, publish, cancel, and a
 * poster image. Every change is audited.
 */
@Service
public class EventAdminService {

  private static final Logger log = LoggerFactory.getLogger(EventAdminService.class);

  /** Poster width: sharp on the event page, small enough to load fast. */
  private static final int POSTER_WIDTH = 1200;

  /** An event with how many have a place and how many are waiting. */
  public record EventWithCounts(LibraryEvent event, long registered, long waitlisted) {}

  private final LibraryEventRepository events;
  private final EventRegistrationRepository registrations;
  private final FileStorage storage;
  private final EventRegistrationService signUps;
  private final ApplicationEventPublisher publisher;
  private final AuditService audit;
  private final LibraryTime time;

  public EventAdminService(
      LibraryEventRepository events,
      EventRegistrationRepository registrations,
      FileStorage storage,
      EventRegistrationService signUps,
      ApplicationEventPublisher publisher,
      AuditService audit,
      LibraryTime time) {
    this.events = events;
    this.registrations = registrations;
    this.storage = storage;
    this.signUps = signUps;
    this.publisher = publisher;
    this.audit = audit;
    this.time = time;
  }

  @Transactional(readOnly = true)
  public Page<EventWithCounts> list(int page, int size) {
    return events.findAllByOrderByStartsAtDesc(PageRequest.of(page, size)).map(this::withCounts);
  }

  @Transactional(readOnly = true)
  public EventWithCounts get(UUID id) {
    return withCounts(require(id));
  }

  /** A new event, as a draft only staff can see. */
  @Transactional
  public LibraryEvent create(Details details, UUID staffId) {
    LibraryEvent event = events.save(LibraryEvent.draft(clean(details), staffId));
    audit.record(
        "event.created", staffId, "event", event.getId(), Map.of("title", event.getTitle()));
    return event;
  }

  /**
   * Changes the details. The number of places can't go below the members who already have one
   * (they'd lose it without being asked); more places are handed to the waitlist (step 332).
   */
  @Transactional
  public LibraryEvent update(UUID id, Details details, UUID staffId) {
    LibraryEvent event = locked(id);
    if (details.registrationRequired() && details.capacity() != null) {
      long taken = placesTaken(id);
      if (details.capacity() < taken) {
        throw new FieldValidationException(
            "capacity",
            "CAPACITY_BELOW_REGISTERED",
            taken + " members already have a place: the number of places can't be lower.");
      }
    }
    event.update(clean(details));
    // More places (or no limit now): the waitlist moves up straight away (step 332)
    int promoted = signUps.promoteFromWaitlist(event);
    audit.record(
        "event.updated",
        staffId,
        "event",
        id,
        Map.of("title", event.getTitle(), "promotedFromWaitlist", promoted));
    return event;
  }

  /** On the website and open for sign-up. Not for an event that has already started. */
  @Transactional
  public LibraryEvent publish(UUID id, UUID staffId) {
    LibraryEvent event = locked(id);
    Instant now = time.now();
    if (!event.getStartsAt().isAfter(now)) {
      throw new BusinessRuleException(
          "EVENT_ALREADY_STARTED", "This event has already started; change the date first.");
    }
    event.publish(now);
    audit.record("event.published", staffId, "event", id, Map.of("title", event.getTitle()));
    return event;
  }

  /** Called off: everyone with a place or on the waitlist is told why (step 333). */
  @Transactional
  public LibraryEvent cancel(UUID id, String reason, UUID staffId) {
    LibraryEvent event = locked(id);
    event.cancel(reason);
    List<UUID> members =
        registrations
            .findByEventIdAndStatusInOrderByRegisteredAt(id, EventRegistrationRepository.ACTIVE)
            .stream()
            .map(EventRegistration::getMemberId)
            .toList();
    publisher.publishEvent(
        new EventCancelled(
            id, event.getTitle(), event.getStartsAt(), event.getCancelReason(), members));
    audit.record(
        "event.cancelled",
        staffId,
        "event",
        id,
        Map.of("reason", event.getCancelReason(), "membersTold", members.size()));
    return event;
  }

  /** A poster picture (JPEG/PNG), stored as one JPEG; replaces the old one. */
  @Transactional
  public LibraryEvent uploadImage(UUID id, InputStream content, long sizeBytes, UUID staffId) {
    LibraryEvent event = require(id);
    BufferedImage image = Pictures.read("image", content, sizeBytes);
    byte[] jpeg = Pictures.jpeg(image, POSTER_WIDTH);
    String key = "events/" + id + "/" + UUID.randomUUID() + ".jpg";
    try {
      storage.put(key, new ByteArrayInputStream(jpeg), jpeg.length, "image/jpeg");
    } catch (IOException e) {
      throw new UncheckedIOException("Could not store the picture", e);
    }
    String old = event.getImageKey();
    event.setImageKey(key);
    deleteQuietly(old);
    audit.record("event.image_changed", staffId, "event", id, Map.of());
    return event;
  }

  @Transactional
  public LibraryEvent removeImage(UUID id, UUID staffId) {
    LibraryEvent event = require(id);
    deleteQuietly(event.getImageKey());
    event.setImageKey(null);
    audit.record("event.image_removed", staffId, "event", id, Map.of());
    return event;
  }

  /**
   * The poster's bytes. Staff see any event's; the public only published or cancelled ones.
   *
   * @param publicOnly true for the public website
   */
  @Transactional(readOnly = true)
  public byte[] image(UUID id, boolean publicOnly) {
    LibraryEvent event = require(id);
    if (event.getImageKey() == null || (publicOnly && event.getStatus() == EventStatus.DRAFT)) {
      throw new NotFoundException("EVENT_IMAGE_NOT_FOUND", "This event has no picture.");
    }
    try (InputStream in = storage.get(event.getImageKey())) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new NotFoundException("EVENT_IMAGE_NOT_FOUND", "This event has no picture.");
    }
  }

  private long placesTaken(UUID eventId) {
    return signUps.placesTaken(eventId);
  }

  private EventWithCounts withCounts(LibraryEvent event) {
    return new EventWithCounts(
        event,
        placesTaken(event.getId()),
        registrations.countByEventIdAndStatusIn(
            event.getId(), List.of(RegistrationStatus.WAITLISTED)));
  }

  /** The description as safe HTML (step 336): only simple formatting and links survive. */
  private static Details clean(Details d) {
    return new Details(
        d.title(),
        RichText.clean(d.description()),
        d.startsAt(),
        d.endsAt(),
        d.venue(),
        d.audience(),
        d.registrationRequired(),
        d.capacity());
  }

  /** Locked so a change and a sign-up can't overlap (e.g. fewer places vs. the last place). */
  private LibraryEvent locked(UUID id) {
    return events
        .findByIdForUpdate(id)
        .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Event not found."));
  }

  private LibraryEvent require(UUID id) {
    return events
        .findById(id)
        .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Event not found."));
  }

  /** An old picture is only clutter; failing to delete it must not undo the new one. */
  private void deleteQuietly(String key) {
    if (key == null) {
      return;
    }
    try {
      storage.delete(key);
    } catch (IOException | RuntimeException e) {
      log.warn("Could not delete old event picture {}", key, e);
    }
  }
}
