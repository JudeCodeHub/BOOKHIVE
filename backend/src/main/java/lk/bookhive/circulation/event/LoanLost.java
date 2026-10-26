package lk.bookhive.circulation.event;

import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.Money;

/**
 * A borrower lost a copy (checklist step 229). The fine module charges {@code replacementCost} and
 * {@code processingFee} (PRD §4.4); both are cancelled if the copy turns up (step 230).
 */
public record LoanLost(
    UUID loanId,
    UUID memberId,
    UUID copyId,
    UUID bookId,
    String title,
    Money replacementCost,
    Money processingFee,
    Instant declaredAt,
    UUID staffId) {}
