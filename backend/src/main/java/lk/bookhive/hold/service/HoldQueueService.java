package lk.bookhive.hold.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.hold.domain.Hold;
import lk.bookhive.hold.domain.HoldStatus;
import lk.bookhive.hold.repository.HoldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The queue of a title (PRD §4.3, HOLD-2, checklist step 252): first come, first served by {@code
 * placed_at}. Only WAITING holds are in the queue; a READY hold has left it (its copy is on the
 * hold shelf), so everyone behind moves up one.
 */
@Service
public class HoldQueueService {

  /**
   * A hold with where it stands.
   *
   * @param position 1 = next to get a copy; null when it isn't waiting (READY or closed)
   * @param queueLength how many are waiting for the title in total
   */
  public record HoldView(Hold hold, Integer position, long queueLength) {}

  private final HoldRepository holds;

  public HoldQueueService(HoldRepository holds) {
    this.holds = holds;
  }

  /** 1 for the first in line; null when the hold isn't waiting. */
  @Transactional(readOnly = true)
  public Integer positionOf(Hold hold) {
    if (hold.getStatus() != HoldStatus.WAITING) {
      return null;
    }
    return (int) holds.countWaitingAhead(hold.getBookId(), hold.getPlacedAt(), hold.getId()) + 1;
  }

  /** How many members are waiting for this title ("3 people ahead of you"). */
  @Transactional(readOnly = true)
  public long queueLength(UUID bookId) {
    return holds.countByBookIdAndStatus(bookId, HoldStatus.WAITING);
  }

  /** Who gets the next copy of this title, if anyone is waiting. */
  @Transactional(readOnly = true)
  public Optional<Hold> firstInLine(UUID bookId) {
    return holds.findFirstByBookIdAndStatusOrderByPlacedAtAscIdAsc(bookId, HoldStatus.WAITING);
  }

  public HoldView view(Hold hold) {
    return new HoldView(hold, positionOf(hold), queueLength(hold.getBookId()));
  }

  /** A member's open holds (READY first, then by when they were placed), with positions. */
  @Transactional(readOnly = true)
  public List<HoldView> openHoldsOf(UUID memberId) {
    return holds.findByMemberIdAndStatusInOrderByPlacedAt(memberId, HoldRepository.OPEN).stream()
        .map(this::view)
        .sorted(
            (a, b) ->
                Boolean.compare(
                    b.hold().getStatus() == HoldStatus.READY,
                    a.hold().getStatus() == HoldStatus.READY))
        .toList();
  }
}
