package lk.bookhive.hold.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.hold.domain.Hold;
import lk.bookhive.hold.domain.HoldStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldRepository extends JpaRepository<Hold, UUID> {

  /** Statuses of a hold that is still open. */
  List<HoldStatus> OPEN = List.of(HoldStatus.WAITING, HoldStatus.READY);

  /** First in line for a title (FIFO). */
  Optional<Hold> findFirstByBookIdAndStatusOrderByPlacedAt(UUID bookId, HoldStatus status);

  /** Queue position: how many are waiting ahead of this time (position = this + 1). */
  long countByBookIdAndStatusAndPlacedAtBefore(UUID bookId, HoldStatus status, Instant placedAt);

  /** Queue length of a title ("3 people waiting"). */
  long countByBookIdAndStatus(UUID bookId, HoldStatus status);

  /** For the max-holds limit. */
  long countByMemberIdAndStatusIn(UUID memberId, Collection<HoldStatus> statuses);

  /** "Already in the queue for this book?" */
  Optional<Hold> findFirstByMemberIdAndBookIdAndStatusIn(
      UUID memberId, UUID bookId, Collection<HoldStatus> statuses);

  /** Who a copy on the hold shelf is set aside for. */
  Optional<Hold> findFirstByAssignedCopyIdAndStatus(UUID copyId, HoldStatus status);

  /** "My holds" and the staff member-profile tab: open holds, oldest first. */
  List<Hold> findByMemberIdAndStatusInOrderByPlacedAt(
      UUID memberId, Collection<HoldStatus> statuses);

  /** Hold shelf: READY holds, soonest deadline first. */
  Page<Hold> findByStatusOrderByExpiresAtAscReadyAtAsc(HoldStatus status, Pageable pageable);

  /** Nightly job: READY holds whose last pickup day has passed. */
  List<Hold> findByStatusAndExpiresAtBefore(HoldStatus status, LocalDate today);
}
