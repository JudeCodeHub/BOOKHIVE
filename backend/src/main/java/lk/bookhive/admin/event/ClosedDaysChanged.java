package lk.bookhive.admin.event;

import java.time.LocalDate;

/**
 * Days from {@code from} on may now be closed: a holiday was added or moved, or the weekly hours
 * changed (step 357). Due dates and hold pickup days were worked out with the old calendar, so
 * circulation and holds move those that now fall on a closed day to the next open day. Published
 * inside the admin's transaction: the change and the moved dates are saved together.
 */
public record ClosedDaysChanged(LocalDate from) {}
