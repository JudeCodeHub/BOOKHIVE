package lk.bookhive.hold.service;

import lk.bookhive.circulation.event.LoanCreated;
import lk.bookhive.hold.domain.HoldStatus;
import lk.bookhive.hold.repository.HoldRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A member borrowed a title they had a hold on (step 254). Runs inside the checkout's transaction
 * (a plain {@code @EventListener}), so the loan and the hold change together:
 *
 * <ul>
 *   <li>READY, and they took the copy set aside for them: the hold is FULFILLED;
 *   <li>READY, but they took another copy: FULFILLED too, and the set-aside copy is passed on to
 *       the next in line (or back to the shelf);
 *   <li>still WAITING (a copy turned up on the shelf): no need to wait any more, so it's cancelled.
 * </ul>
 *
 * Checkout itself already refuses a copy on the hold shelf for anyone but its holder.
 */
@Component
public class HoldLoanListener {

  private final HoldRepository holds;
  private final HoldAssignmentService assignment;

  public HoldLoanListener(HoldRepository holds, HoldAssignmentService assignment) {
    this.holds = holds;
    this.assignment = assignment;
  }

  @EventListener
  public void onLoanCreated(LoanCreated loan) {
    holds
        .findFirstByMemberIdAndBookIdAndStatusIn(
            loan.memberId(), loan.bookId(), HoldRepository.OPEN)
        .ifPresent(
            hold -> {
              if (hold.getStatus() == HoldStatus.READY) {
                var setAside = hold.getAssignedCopyId();
                hold.fulfil(loan.loanId(), loan.checkedOutAt());
                if (!setAside.equals(loan.copyId())) {
                  holds.flush();
                  assignment.passOn(
                      setAside,
                      loan.bookId(),
                      "Holder borrowed another copy",
                      loan.staffId(),
                      loan.checkedOutAt());
                }
              } else {
                hold.cancel(null, "Borrowed a copy from the shelf", loan.checkedOutAt());
              }
            });
  }
}
