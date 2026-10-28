package lk.bookhive.event.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.event.domain.EventRegistration;
import lk.bookhive.event.domain.LibraryEvent;
import lk.bookhive.event.domain.RegistrationStatus;
import lk.bookhive.event.event.EventPlaceConfirmed;
import lk.bookhive.event.repository.EventRegistrationRepository;
import lk.bookhive.event.repository.LibraryEventRepository;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.member.service.BorrowerService.Borrower;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signing up for events (EVT-2, checklist step 332): a place while there's room, otherwise the
 * waitlist, first come first served. Whenever room appears (more places, a cancellation) the
 * waitlist moves up on its own. The event row is locked while places are counted, so two members
 * can't both take the last one.
 */
@Service
public class EventRegistrationService {

  /** "My events" shows at most this many sign-ups (a member's whole history is rarely more). */
  static final int MY_EVENTS_LIMIT = 100;

  /**
   * A member's registration with where they stand.
   *
   * @param waitlistPosition 1 = next to get a place; null when they have one (or it's closed)
   */
  public record RegistrationView(
      EventRegistration registration, LibraryEvent event, Integer waitlistPosition) {}

  private final LibraryEventRepository events;
  private final EventRegistrationRepository registrations;
  private final BorrowerService borrowers;
  private final ApplicationEventPublisher publisher;
  private final AuditService audit;
  private final LibraryTime time;

  public EventRegistrationService(
      LibraryEventRepository events,
      EventRegistrationRepository registrations,
      BorrowerService borrowers,
      ApplicationEventPublisher publisher,
      AuditService audit,
      LibraryTime time) {
    this.events = events;
    this.registrations = registrations;
    this.borrowers = borrowers;
    this.publisher = publisher;
    this.audit = audit;
    this.time = time;
  }

  /**
   * Signs the member up: a place, or the waitlist when the event is full.
   *
   * @throws BusinessRuleException when sign-up is closed or the membership isn't active
   * @throws ConflictException ALREADY_REGISTERED when they're signed up already
   */
  @Transactional
  public RegistrationView register(UUID eventId, UUID memberId) {
    LibraryEvent event = locked(eventId);
    Instant now = time.now();
    if (!event.isOpenForRegistration(now)) {
      throw new BusinessRuleException(
          "REGISTRATION_CLOSED",
          event.isRegistrationRequired()
              ? "Sign-up for this event is closed."
              : "No sign-up needed for this event: just come along.");
    }
    requireActiveMember(memberId);
    if (registrations
        .findFirstByEventIdAndMemberIdAndStatusNot(eventId, memberId, RegistrationStatus.CANCELLED)
        .isPresent()) {
      throw new ConflictException("ALREADY_REGISTERED", "You're already signed up for this event.");
    }
    boolean placeFree = !event.hasLimitedPlaces() || placesTaken(eventId) < event.getCapacity();
    EventRegistration registration;
    try {
      registration =
          registrations.saveAndFlush(EventRegistration.signUp(eventId, memberId, placeFree, now));
    } catch (DataIntegrityViolationException e) {
      // A double click: the unique index (one active sign-up per member) caught the second
      throw new ConflictException("ALREADY_REGISTERED", "You're already signed up for this event.");
    }
    if (placeFree) {
      publishConfirmed(event, registration, false);
    }
    audit.record(
        "event.registered",
        null,
        "event",
        eventId,
        Map.of("memberId", memberId.toString(), "status", registration.getStatus().name()));
    return view(registration, event);
  }

  /**
   * Gives free places to the waitlist, oldest first, until the event is full or nobody is waiting.
   * Runs inside the caller's transaction, which has locked the event (more places, a cancellation).
   *
   * @return how many moved up
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public int promoteFromWaitlist(LibraryEvent event) {
    if (!event.isOpenForRegistration(time.now())) {
      return 0;
    }
    Instant now = time.now();
    int promoted = 0;
    long taken = placesTaken(event.getId());
    while (!event.hasLimitedPlaces() || taken < event.getCapacity()) {
      var next =
          registrations.findFirstByEventIdAndStatusOrderByRegisteredAtAscIdAsc(
              event.getId(), RegistrationStatus.WAITLISTED);
      if (next.isEmpty()) {
        break;
      }
      EventRegistration registration = next.get();
      registration.promote(now);
      registrations.flush();
      publishConfirmed(event, registration, true);
      taken++;
      promoted++;
    }
    return promoted;
  }

  /**
   * The member gives up their place or leaves the waitlist (step 333), any time before the event
   * starts. A freed place goes to the first on the waitlist straight away.
   */
  @Transactional
  public void cancel(UUID eventId, UUID memberId) {
    LibraryEvent event = locked(eventId);
    EventRegistration registration =
        registrations
            .findFirstByEventIdAndMemberIdAndStatusNot(
                eventId, memberId, RegistrationStatus.CANCELLED)
            .filter(r -> r.getStatus().isActive())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "REGISTRATION_NOT_FOUND", "You aren't signed up for this event."));
    if (!time.now().isBefore(event.getStartsAt())) {
      throw new BusinessRuleException("EVENT_ALREADY_STARTED", "This event has already started.");
    }
    boolean freedPlace = registration.cancel(time.now());
    registrations.flush();
    int promoted = freedPlace ? promoteFromWaitlist(event) : 0;
    audit.record(
        "event.registration_cancelled",
        null,
        "event",
        eventId,
        Map.of("memberId", memberId.toString(), "promotedFromWaitlist", promoted));
  }

  /** The member's current registration for an event, if any (event page: "you're signed up"). */
  @Transactional(readOnly = true)
  public java.util.Optional<RegistrationView> current(UUID eventId, UUID memberId) {
    return registrations
        .findFirstByEventIdAndMemberIdAndStatusNot(eventId, memberId, RegistrationStatus.CANCELLED)
        .map(r -> view(r, require(eventId)));
  }

  /**
   * "My events" (step 343): the member's sign-ups that weren't given up, newest sign-up first,
   * including past ones and events the library cancelled.
   */
  @Transactional(readOnly = true)
  public List<RegistrationView> mine(UUID memberId) {
    List<EventRegistration> mine =
        registrations.findByMemberIdOrderByRegisteredAtDesc(memberId).stream()
            .filter(r -> r.getStatus() != RegistrationStatus.CANCELLED)
            .limit(MY_EVENTS_LIMIT)
            .toList();
    Map<UUID, LibraryEvent> byId =
        events.findAllById(mine.stream().map(EventRegistration::getEventId).toList()).stream()
            .collect(Collectors.toMap(LibraryEvent::getId, Function.identity()));
    return mine.stream().map(r -> view(r, byId.get(r.getEventId()))).toList();
  }

  /** Places taken: REGISTERED plus already ATTENDED. */
  public long placesTaken(UUID eventId) {
    return registrations.countByEventIdAndStatusIn(
        eventId, List.of(RegistrationStatus.REGISTERED, RegistrationStatus.ATTENDED));
  }

  RegistrationView view(EventRegistration registration, LibraryEvent event) {
    Integer position = null;
    if (registration.getStatus() == RegistrationStatus.WAITLISTED) {
      List<EventRegistration> waiting =
          registrations.findByEventIdAndStatusInOrderByRegisteredAt(
              event.getId(), List.of(RegistrationStatus.WAITLISTED));
      position = waiting.indexOf(registration) + 1;
    }
    return new RegistrationView(registration, event, position);
  }

  private void requireActiveMember(UUID memberId) {
    Borrower borrower =
        borrowers
            .byId(memberId)
            .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found."));
    LocalDate today = time.today();
    boolean expired = borrower.expiresAt() != null && borrower.expiresAt().isBefore(today);
    if (borrower.status() != MemberStatus.ACTIVE || expired) {
      throw new BusinessRuleException(
          "MEMBERSHIP_NOT_ACTIVE", "Only members with an active membership can sign up.");
    }
  }

  private void publishConfirmed(
      LibraryEvent event, EventRegistration registration, boolean fromWaitlist) {
    publisher.publishEvent(
        new EventPlaceConfirmed(
            registration.getId(),
            event.getId(),
            registration.getMemberId(),
            event.getTitle(),
            event.getStartsAt(),
            event.getVenue(),
            fromWaitlist));
  }

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
}
