package lk.bookhive.event.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Staff cancelled an event (checklist step 331); everyone who had a place or was waiting is told
 * why (step 333).
 *
 * @param memberIds members whose registration was active
 */
public record EventCancelled(
    UUID eventId, String title, Instant startsAt, String reason, List<UUID> memberIds) {}
