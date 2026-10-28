package lk.bookhive.event.event;

import java.time.Instant;
import java.util.UUID;

/** A member has a place at an event tomorrow (daily job, checklist step 333). */
public record EventReminderDue(
    UUID registrationId,
    UUID eventId,
    UUID memberId,
    String title,
    Instant startsAt,
    String venue) {}
