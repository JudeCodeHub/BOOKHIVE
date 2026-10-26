package lk.bookhive.circulation.event;

import java.time.LocalDate;
import java.util.UUID;

/** A loan got a new due date (checklist step 226), e.g. to update scheduled reminders. */
public record LoanRenewed(
    UUID loanId,
    UUID memberId,
    UUID copyId,
    LocalDate oldDueDate,
    LocalDate newDueDate,
    int renewCount) {}
