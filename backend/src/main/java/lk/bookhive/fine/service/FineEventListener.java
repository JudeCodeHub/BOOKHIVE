package lk.bookhive.fine.service;

import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.circulation.event.LoanDamaged;
import lk.bookhive.circulation.event.LoanLost;
import lk.bookhive.circulation.event.LoanReturned;
import lk.bookhive.circulation.event.LostLoanFound;
import lk.bookhive.circulation.service.FineCalculator;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.member.event.MembershipFeeCharged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns what happens at the desk into ledger lines (steps 272-273). Plain {@code @EventListener}s:
 * they run inside the transaction that published the event, so a return and its fine (or a lost
 * book and its charge) are saved together, or neither is.
 */
@Component
public class FineEventListener {

  private final FineChargeService charges;
  private final SettingsService settings;
  private final LibraryTime time;

  public FineEventListener(FineChargeService charges, SettingsService settings, LibraryTime time) {
    this.charges = charges;
    this.settings = settings;
    this.time = time;
  }

  /** Step 272: the overdue fine is fixed at what the return desk worked out. */
  @EventListener
  public void onReturned(LoanReturned event) {
    charges.finaliseOverdue(
        event.loanId(),
        event.memberId(),
        event.title(),
        event.lateFine(),
        "Returned " + event.daysLate() + " day(s) late",
        event.staffId());
  }

  /** A lost book stops the overdue clock (charged up to today) and is charged for (step 273). */
  @EventListener
  public void onLost(LoanLost event) {
    var lateFine = FineCalculator.lateFine(event.dueDate(), time.today(), settings.fines());
    charges.finaliseOverdue(
        event.loanId(),
        event.memberId(),
        event.title(),
        lateFine.amount(),
        "Overdue until reported lost",
        event.staffId());
    charges.chargeLost(
        event.loanId(),
        event.memberId(),
        event.title(),
        event.replacementCost(),
        event.processingFee(),
        event.staffId());
  }

  @EventListener
  public void onFound(LostLoanFound event) {
    charges.cancelLost(event.loanId(), event.staffId());
  }

  @EventListener
  public void onDamaged(LoanDamaged event) {
    charges.chargeDamage(
        event.loanId(),
        event.memberId(),
        event.title(),
        event.amount(),
        event.note(),
        event.staffId());
  }

  @EventListener
  public void onMembershipFee(MembershipFeeCharged event) {
    charges.chargeMembershipFee(event.memberId(), event.amount(), event.validUntil());
  }
}
