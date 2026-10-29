package lk.bookhive.event.api;

import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.storage.Pictures;
import lk.bookhive.event.domain.EventAudience;
import lk.bookhive.event.domain.EventStatus;
import lk.bookhive.event.domain.LibraryEvent;
import lk.bookhive.event.domain.RegistrationStatus;
import lk.bookhive.event.service.EventQueryService.PublicEvent;
import lk.bookhive.event.service.EventRegistrationService.RegistrationView;

/** What the public and member event endpoints return. */
public final class EventResponses {

  private EventResponses() {}

  /**
   * An event on the website.
   *
   * @param placesLeft null when there's no limit
   * @param imageVersion changes when the poster changes (use as ?v= on the image URL); null = none
   * @param openForRegistration members can sign up right now
   */
  public record EventSummary(
      UUID id,
      String title,
      String description,
      Instant startsAt,
      Instant endsAt,
      String venue,
      EventAudience audience,
      boolean registrationRequired,
      Integer capacity,
      Long placesLeft,
      long waitlisted,
      EventStatus status,
      String cancelReason,
      String imageVersion,
      boolean openForRegistration) {

    static EventSummary from(PublicEvent row, Instant now) {
      LibraryEvent e = row.event();
      return new EventSummary(
          e.getId(),
          e.getTitle(),
          e.getDescription(),
          e.getStartsAt(),
          e.getEndsAt(),
          e.getVenue(),
          e.getAudience(),
          e.isRegistrationRequired(),
          e.getCapacity(),
          row.placesLeft(),
          row.waitlisted(),
          e.getStatus(),
          e.getCancelReason(),
          posterVersion(e),
          e.isOpenForRegistration(now));
    }
  }

  /**
   * A member's sign-up.
   *
   * @param waitlistPosition 1 = next to get a place; null when they have one
   * @param checkInCode for the QR ticket; null while waitlisted
   */
  public record RegistrationSummary(
      UUID registrationId,
      UUID eventId,
      String eventTitle,
      Instant startsAt,
      Instant endsAt,
      String venue,
      EventStatus eventStatus,
      String imageVersion,
      RegistrationStatus status,
      Integer waitlistPosition,
      Instant registeredAt,
      String checkInCode) {

    static RegistrationSummary from(RegistrationView view) {
      var r = view.registration();
      boolean hasPlace =
          r.getStatus() == RegistrationStatus.REGISTERED
              || r.getStatus() == RegistrationStatus.ATTENDED;
      return new RegistrationSummary(
          r.getId(),
          r.getEventId(),
          view.event().getTitle(),
          view.event().getStartsAt(),
          view.event().getEndsAt(),
          view.event().getVenue(),
          view.event().getStatus(),
          posterVersion(view.event()),
          r.getStatus(),
          view.waitlistPosition(),
          r.getRegisteredAt(),
          hasPlace ? r.getCheckInCode() : null);
    }
  }

  /** Changes with every new poster; null when there's none. */
  static String posterVersion(LibraryEvent event) {
    return Pictures.version(event.getImageKey());
  }
}
