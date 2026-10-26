package lk.bookhive.circulation.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lk.bookhive.common.domain.Money;

/**
 * A copy came back (checklist step 225). The fine module records {@code lateFine} when it isn't
 * zero; the hold module learns that the copy is on the hold shelf ({@code heldForMemberId}).
 *
 * @param heldForMemberId the member it was set aside for, or null if it went back to the shelf
 */
public record LoanReturned(
    UUID loanId,
    UUID memberId,
    UUID copyId,
    UUID bookId,
    String title,
    LocalDate dueDate,
    Instant returnedAt,
    long daysLate,
    Money lateFine,
    UUID heldForMemberId,
    UUID staffId) {}
