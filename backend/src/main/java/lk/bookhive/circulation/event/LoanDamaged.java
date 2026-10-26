package lk.bookhive.circulation.event;

import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.Money;

/** A copy came back damaged (checklist step 229); the fine module charges {@code amount}. */
public record LoanDamaged(
    UUID loanId,
    UUID memberId,
    UUID copyId,
    UUID bookId,
    String title,
    Money amount,
    String note,
    Instant reportedAt,
    UUID staffId) {}
