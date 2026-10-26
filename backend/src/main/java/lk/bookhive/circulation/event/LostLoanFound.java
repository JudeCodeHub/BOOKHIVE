package lk.bookhive.circulation.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A copy marked lost turned up and was returned (checklist step 230). The fine module cancels the
 * lost charges from {@link LoanLost} if they're unpaid, or refunds them if they were paid. Any late
 * fine comes separately with the {@link LoanReturned} published for the same return.
 */
public record LostLoanFound(
    UUID loanId,
    UUID memberId,
    UUID copyId,
    UUID bookId,
    String title,
    Instant foundAt,
    UUID staffId) {}
