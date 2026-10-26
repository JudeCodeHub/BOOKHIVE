package lk.bookhive.circulation.event;

import java.time.LocalDate;
import java.util.UUID;

/** A loan passed its due date (nightly job, step 233), e.g. for the overdue notice email. */
public record LoanBecameOverdue(UUID loanId, UUID memberId, UUID copyId, LocalDate dueDate) {}
