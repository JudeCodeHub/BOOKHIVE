package lk.bookhive.circulation.service;

import java.util.Optional;
import java.util.UUID;
import lk.bookhive.common.domain.Money;

/**
 * Questions circulation asks modules that are built later (fines, holds). Until a module provides a
 * bean of its interface, EligibilityChecker uses a default answer ("nothing owed", "nothing
 * reserved"), so the eligibility rules can be complete now.
 */
public final class BorrowingPorts {

  private BorrowingPorts() {}

  /** How much a member owes in unpaid fines and fees (fine module). */
  public interface OutstandingBalance {
    Money of(UUID memberId);
  }

  /**
   * Is a member waiting for this title? The returned copy then goes to the hold shelf for them
   * (hold module; the hold module also records which copy was set aside).
   */
  public interface HoldQueue {
    Optional<UUID> nextMemberFor(UUID bookId, UUID returnedCopyId);
  }

  /** Has anyone reserved this title? Then loans of it can't be renewed (PRD §4.2). */
  public interface HoldDemand {
    boolean isWanted(UUID bookId);
  }

  /** Which member a copy on the hold shelf is waiting for (hold module). */
  public interface HoldShelf {
    Optional<UUID> reservedFor(UUID copyId);
  }
}
