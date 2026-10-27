package lk.bookhive.circulation.event;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A member has books due on {@code dueDate}, a few days from now (daily job, checklist step 314).
 * One event per member and due date, listing all those books; the notification module sends one
 * reminder for them.
 */
public record LoansDueSoon(UUID memberId, UUID userId, LocalDate dueDate, List<Book> books) {

  public record Book(UUID loanId, String title) {}
}
