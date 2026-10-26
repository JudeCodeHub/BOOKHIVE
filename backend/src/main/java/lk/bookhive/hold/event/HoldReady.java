package lk.bookhive.hold.event;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A copy is on the hold shelf for this member (HOLD-3, step 259): tell them to pick it up by {@code
 * pickUpBy}. Published inside the transaction; notification listeners act after commit.
 */
public record HoldReady(
    UUID holdId, UUID memberId, UUID bookId, UUID copyId, String title, LocalDate pickUpBy) {}
