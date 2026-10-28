package lk.bookhive.notification.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.event.event.EventCancelled;
import lk.bookhive.event.event.EventPlaceConfirmed;
import lk.bookhive.event.event.EventReminderDue;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/**
 * Library event notices (checklist step 333): a place from the waitlist, a cancelled event (with
 * the reason) and the day-before reminder. Each once per member (deduplicated).
 */
@Component
public class EventNotificationListener {

  private final MemberNotifier members;

  public EventNotificationListener(MemberNotifier members) {
    this.members = members;
  }

  /** Only when they moved up from the waitlist: a direct sign-up sees its place straight away. */
  @NotificationListener
  public void onPlaceConfirmed(EventPlaceConfirmed event) {
    if (!event.fromWaitlist()) {
      return;
    }
    members.send(
        event.memberId(),
        NotificationType.EVENT_PLACE_CONFIRMED,
        params(event.title(), event.startsAt(), event.venue()),
        "/events/" + event.eventId(),
        "EVENT_PLACE:" + event.registrationId());
  }

  @NotificationListener
  public void onCancelled(EventCancelled event) {
    Map<String, Object> params = params(event.title(), event.startsAt(), "");
    params.put("reason", event.reason());
    for (UUID memberId : event.memberIds()) {
      members.send(
          memberId,
          NotificationType.EVENT_CANCELLED,
          params,
          "/events",
          "EVENT_CANCELLED:" + event.eventId() + ":" + memberId);
    }
  }

  @NotificationListener
  public void onReminder(EventReminderDue event) {
    members.send(
        event.memberId(),
        NotificationType.EVENT_REMINDER,
        params(event.title(), event.startsAt(), event.venue()),
        "/events/" + event.eventId(),
        "EVENT_REMINDER:" + event.registrationId());
  }

  /** The start in library time for the email; the day alone for the short in-app text. */
  private static Map<String, Object> params(String title, Instant startsAt, String venue) {
    LocalDateTime local = LocalDateTime.ofInstant(startsAt, LibraryTime.ZONE);
    Map<String, Object> params = new HashMap<>();
    params.put("title", title);
    params.put("startsAt", local);
    params.put("date", local.toLocalDate());
    params.put("venue", venue);
    return params;
  }
}
