package lk.bookhive.circulation.service;

import java.util.UUID;
import lk.bookhive.circulation.repository.LoanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Questions other modules ask about loans (e.g. the hold rules, step 251). */
@Service
public class LoanLookupService {

  private final LoanRepository loans;

  public LoanLookupService(LoanRepository loans) {
    this.loans = loans;
  }

  /** Has the member got a copy of this title out now (on time or overdue)? */
  @Transactional(readOnly = true)
  public boolean hasBookOut(UUID memberId, UUID bookId) {
    return loans.memberHasBookOut(memberId, bookId);
  }
}
