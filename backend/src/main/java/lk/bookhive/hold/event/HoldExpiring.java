package lk.bookhive.hold.event;

import java.time.LocalDate;
import java.util.UUID;

/** Reminder: the member's copy leaves the hold shelf after {@code pickUpBy} (step 259). */
public record HoldExpiring(
    UUID holdId, UUID memberId, UUID bookId, String title, LocalDate pickUpBy) {}
