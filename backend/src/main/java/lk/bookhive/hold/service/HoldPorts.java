package lk.bookhive.hold.service;

import java.util.Optional;
import java.util.UUID;
import lk.bookhive.catalog.service.AvailabilityService.HoldsWaiting;
import lk.bookhive.circulation.service.BorrowingPorts.HoldDemand;
import lk.bookhive.circulation.service.BorrowingPorts.HoldQueue;
import lk.bookhive.circulation.service.BorrowingPorts.HoldShelf;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.hold.domain.Hold;
import lk.bookhive.hold.domain.HoldStatus;
import lk.bookhive.hold.repository.HoldRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The hold module's answers to circulation's questions (steps 253-254), replacing the "nobody is
 * waiting" defaults in {@code BorrowingPorts}:
 *
 * <ul>
 *   <li>a copy was returned: who gets it? (sets their hold READY, in the return's transaction, so
 *       the desk's routing message and the copy's status are right at once);
 *   <li>a copy on the hold shelf: who is it for? (checkout lends it only to them);
 *   <li>is anyone waiting for this title? (then loans of it can't be renewed).
 * </ul>
 */
@Component
public class HoldPorts implements HoldQueue, HoldShelf, HoldDemand, HoldsWaiting {

  private final HoldAssignmentService assignment;
  private final HoldRepository holds;
  private final LibraryTime time;

  public HoldPorts(HoldAssignmentService assignment, HoldRepository holds, LibraryTime time) {
    this.assignment = assignment;
    this.holds = holds;
    this.time = time;
  }

  @Override
  @Transactional
  public Optional<UUID> nextMemberFor(UUID bookId, UUID returnedCopyId) {
    return assignment.assignToNext(bookId, returnedCopyId, time.now()).map(Hold::getMemberId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> reservedFor(UUID copyId) {
    return holds
        .findFirstByAssignedCopyIdAndStatus(copyId, HoldStatus.READY)
        .map(Hold::getMemberId);
  }

  /** The queue length on the public book page ("2 people waiting", step 261). */
  @Override
  @Transactional(readOnly = true)
  public long forBook(UUID bookId) {
    return holds.countByBookIdAndStatus(bookId, HoldStatus.WAITING);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isWanted(UUID bookId) {
    return holds.countByBookIdAndStatus(bookId, HoldStatus.WAITING) > 0;
  }
}
