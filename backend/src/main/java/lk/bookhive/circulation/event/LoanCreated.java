package lk.bookhive.circulation.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A copy was lent (checklist step 225). Published after the checkout commits, for modules that
 * react to loans: receipt emails, due-soon reminders, statistics.
 */
public record LoanCreated(
    UUID loanId,
    UUID memberId,
    UUID copyId,
    UUID bookId,
    String barcode,
    String title,
    Instant checkedOutAt,
    LocalDate dueDate,
    UUID staffId) {}
