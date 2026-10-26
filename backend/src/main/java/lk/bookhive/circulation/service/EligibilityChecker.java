package lk.bookhive.circulation.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.catalog.domain.CopyStatus;
import lk.bookhive.catalog.domain.LendingType;
import lk.bookhive.catalog.service.LendingCatalogService.LendableCopy;
import lk.bookhive.circulation.service.BorrowingPorts.HoldShelf;
import lk.bookhive.circulation.service.BorrowingPorts.OutstandingBalance;
import lk.bookhive.common.domain.Money;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.service.BorrowerService.Borrower;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Can this member borrow this copy right now? (checklist step 220.) Returns <b>every</b> reason why
 * not, not just the first, so the desk can sort them all out at once: "membership expired, and LKR
 * 650 unpaid".
 *
 * <p>No side effects: the desk calls it while scanning, and checkout calls it again inside its
 * transaction before lending.
 */
@Component
public class EligibilityChecker {

  /** Why a loan isn't allowed. Some can be overridden by staff with a reason (step 228). */
  public enum Reason {
    MEMBER_NOT_ACTIVE(false),
    MEMBERSHIP_EXPIRED(false),
    LOAN_LIMIT_REACHED(true),
    FINES_BLOCK_BORROWING(true),
    COPY_NOT_AVAILABLE(false),
    HELD_FOR_ANOTHER_MEMBER(false),
    COPY_REFERENCE_ONLY(true),
    COPY_RESTRICTED(true),
    SECTION_NOT_ALLOWED(true);

    private final boolean overridable;

    Reason(boolean overridable) {
      this.overridable = overridable;
    }

    public boolean overridable() {
      return overridable;
    }
  }

  /** One reason with a sentence for the desk screen. */
  public record Problem(Reason reason, String message) {}

  private final OutstandingBalance balance;
  private final HoldShelf holdShelf;
  private final SettingsService settings;

  /**
   * The fine and hold modules supply the real answers once they exist; until then nobody owes
   * anything and nothing is reserved.
   */
  public EligibilityChecker(
      ObjectProvider<OutstandingBalance> balance,
      ObjectProvider<HoldShelf> holdShelf,
      SettingsService settings) {
    this.balance = balance.getIfAvailable(() -> memberId -> Money.ZERO);
    this.holdShelf = holdShelf.getIfAvailable(() -> copyId -> Optional.empty());
    this.settings = settings;
  }

  /**
   * Problems with the member, whatever they borrow: status, expiry, fines, and the loan limit.
   *
   * @param itemsOut items they have now, plus any already scanned in this checkout
   */
  public List<Problem> member(Borrower borrower, LocalDate today, int itemsOut) {
    List<Problem> problems = new ArrayList<>();
    if (borrower.status() == MemberStatus.EXPIRED
        || (borrower.expiresAt() != null && borrower.expiresAt().isBefore(today))) {
      problems.add(
          new Problem(
              Reason.MEMBERSHIP_EXPIRED,
              "The membership expired on " + borrower.expiresAt() + ". Renew it first."));
    } else if (borrower.status() != MemberStatus.ACTIVE) {
      problems.add(
          new Problem(
              Reason.MEMBER_NOT_ACTIVE,
              "The membership is " + borrower.status().name().toLowerCase() + "."));
    }

    Money owed = balance.of(borrower.memberId());
    Money limit = new Money(settings.fines().blockBorrowingAt());
    if (owed.isAtLeast(limit)) {
      problems.add(
          new Problem(
              Reason.FINES_BLOCK_BORROWING,
              "LKR "
                  + owed.amount().toPlainString()
                  + " unpaid (borrowing stops at LKR "
                  + limit.amount().toPlainString()
                  + ")."));
    }
    if (itemsOut >= borrower.maxItems()) {
      problems.add(
          new Problem(
              Reason.LOAN_LIMIT_REACHED,
              "Already " + itemsOut + " of " + borrower.maxItems() + " items out."));
    }
    return problems;
  }

  /** Problems with this copy for this member: availability, lending type, section, holds. */
  public List<Problem> copy(Borrower borrower, LendableCopy copy) {
    List<Problem> problems = new ArrayList<>();
    if (copy.status() == CopyStatus.ON_HOLD_SHELF) {
      Optional<UUID> reservedFor = holdShelf.reservedFor(copy.copyId());
      if (reservedFor.isPresent() && !reservedFor.get().equals(borrower.memberId())) {
        problems.add(
            new Problem(
                Reason.HELD_FOR_ANOTHER_MEMBER,
                "This copy is waiting on the hold shelf for another member."));
      }
    } else if (copy.status() != CopyStatus.AVAILABLE) {
      problems.add(
          new Problem(
              Reason.COPY_NOT_AVAILABLE,
              "This copy is " + copy.status().name().toLowerCase().replace('_', ' ') + "."));
    }

    if (copy.lendingType() == LendingType.REFERENCE_ONLY) {
      problems.add(
          new Problem(Reason.COPY_REFERENCE_ONLY, "Reference copy: it stays in the library."));
    } else if (copy.lendingType() == LendingType.RESTRICTED) {
      problems.add(
          new Problem(Reason.COPY_RESTRICTED, "Restricted copy: lending needs staff approval."));
    }

    // Junior members: only the children's and young-adult shelves (PRD §4.1)
    if (copy.sections().stream().noneMatch(borrower::mayBorrowFrom)) {
      problems.add(
          new Problem(
              Reason.SECTION_NOT_ALLOWED,
              "This membership type can't borrow from "
                  + String.join(", ", copy.sections())
                  + "."));
    }
    return problems;
  }

  /** Both checks together, for one copy. */
  public List<Problem> check(Borrower borrower, LendableCopy copy, LocalDate today, int itemsOut) {
    List<Problem> problems = new ArrayList<>(member(borrower, today, itemsOut));
    problems.addAll(copy(borrower, copy));
    return problems;
  }
}
