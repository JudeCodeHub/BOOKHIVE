package lk.bookhive.event.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.event.domain.EventRegistration;
import lk.bookhive.event.domain.EventStatus;
import lk.bookhive.event.domain.LibraryEvent;
import lk.bookhive.event.domain.RegistrationStatus;
import lk.bookhive.event.repository.EventRegistrationRepository;
import lk.bookhive.event.repository.LibraryEventRepository;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.member.service.BorrowerService.Borrower;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checking members in at the door (EVT-3, checklist step 334): staff scan the QR ticket, which
 * holds the registration's random code. Only a confirmed place for this very event, within the
 * check-in window, and only once.
 */
@Service
public class EventCheckInService {

  /** Doors open this long before the start... */
  static final Duration OPENS_BEFORE = Duration.ofHours(2);

  /** ...and latecomers can still be checked in this long after the end. */
  static final Duration CLOSES_AFTER = Duration.ofHours(1);

  /** QR tickets may carry this prefix (step 345) so they aren't confused with other codes. */
  public static final String QR_PREFIX = "BOOKHIVE-EVENT:";

  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  /**
   * Who was checked in.
   *
   * @param attended members checked in so far, this one included
   */
  public record CheckIn(
      EventRegistration registration, Borrower member, String fullName, long attended) {}

  private final LibraryEventRepository events;
  private final EventRegistrationRepository registrations;
  private final BorrowerService borrowers;
  private final AuthService authService;
  private final AuditService audit;
  private final LibraryTime time;

  public EventCheckInService(
      LibraryEventRepository events,
      EventRegistrationRepository registrations,
      BorrowerService borrowers,
      AuthService authService,
      AuditService audit,
      LibraryTime time) {
    this.events = events;
    this.registrations = registrations;
    this.borrowers = borrowers;
    this.authService = authService;
    this.audit = audit;
    this.time = time;
  }

  /**
   * Checks in the ticket holder.
   *
   * @param scanned the QR content: the code, with or without {@link #QR_PREFIX}
   */
  @Transactional
  public CheckIn checkIn(UUID eventId, String scanned, UUID staffId) {
    LibraryEvent event =
        events
            .findById(eventId)
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Event not found."));
    requireCheckInOpen(event);

    String code = scanned.strip();
    if (code.startsWith(QR_PREFIX)) {
      code = code.substring(QR_PREFIX.length());
    }
    EventRegistration registration =
        registrations
            .findByCheckInCode(code)
            .orElseThrow(
                () -> new NotFoundException("TICKET_NOT_FOUND", "This isn't a BookHive ticket."));
    if (!registration.getEventId().equals(eventId)) {
      String other =
          events.findById(registration.getEventId()).map(LibraryEvent::getTitle).orElse("");
      throw new BusinessRuleException(
          "TICKET_OTHER_EVENT", "This ticket is for another event: " + other + ".");
    }
    if (registration.getStatus() == RegistrationStatus.ATTENDED) {
      throw new ConflictException(
          "ALREADY_CHECKED_IN",
          "Already checked in at "
              + LocalTime.ofInstant(registration.getCheckedInAt(), LibraryTime.ZONE).format(HH_MM)
              + ".");
    }
    // REGISTERED only (waitlisted or cancelled tickets are refused by the entity)
    registration.checkIn(staffId, time.now());
    registrations.flush();

    Borrower member = borrowers.byId(registration.getMemberId()).orElse(null);
    String name = member == null ? null : authService.getProfile(member.userId()).getFullName();
    long attended =
        registrations.countByEventIdAndStatusIn(eventId, List.of(RegistrationStatus.ATTENDED));
    audit.record(
        "event.checked_in",
        staffId,
        "event",
        eventId,
        Map.of("registrationId", registration.getId().toString()));
    return new CheckIn(registration, member, name, attended);
  }

  private void requireCheckInOpen(LibraryEvent event) {
    if (event.getStatus() != EventStatus.PUBLISHED) {
      throw new BusinessRuleException(
          "CHECK_IN_CLOSED",
          event.getStatus() == EventStatus.CANCELLED
              ? "This event was cancelled."
              : "This event isn't published.");
    }
    Instant now = time.now();
    if (now.isBefore(event.getStartsAt().minus(OPENS_BEFORE))
        || now.isAfter(event.getEndsAt().plus(CLOSES_AFTER))) {
      throw new BusinessRuleException(
          "CHECK_IN_CLOSED", "Check-in opens 2 hours before the event and closes after it.");
    }
  }
}
