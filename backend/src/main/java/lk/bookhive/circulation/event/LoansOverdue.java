package lk.bookhive.circulation.event;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A member has books that are {@code daysLate} days overdue today (daily job, checklist step 315):
 * one notice on the first late day and one a week later. One event per member and notice day.
 */
public record LoansOverdue(
    UUID memberId, UUID userId, int daysLate, LocalDate noticeDate, List<Book> books) {

  public record Book(UUID loanId, String title, LocalDate dueDate) {}
}
