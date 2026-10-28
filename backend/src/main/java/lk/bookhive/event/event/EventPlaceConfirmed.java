package lk.bookhive.event.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A member got a place at an event: straight away, or later when they moved up from the waitlist
 * (step 332). {@code fromWaitlist} decides whether they need telling (step 333).
 */
public record EventPlaceConfirmed(
    UUID registrationId,
    UUID eventId,
    UUID memberId,
    String title,
    Instant startsAt,
    String venue,
    boolean fromWaitlist) {}
