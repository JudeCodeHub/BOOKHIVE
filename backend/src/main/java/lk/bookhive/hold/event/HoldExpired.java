package lk.bookhive.hold.event;

import java.util.UUID;

/** Not picked up in time; the copy went to the next in line or back to the shelf (step 255). */
public record HoldExpired(UUID holdId, UUID memberId, UUID bookId, String title) {}
