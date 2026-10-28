package lk.bookhive.event.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.event.domain.EventStatus;
import lk.bookhive.event.domain.LibraryEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryEventRepository extends JpaRepository<LibraryEvent, UUID> {

  /** Public list: published events that haven't ended, soonest first. */
  Page<LibraryEvent> findByStatusAndEndsAtAfterOrderByStartsAt(
      EventStatus status, Instant now, Pageable pageable);

  /** Calendar: published events starting in a date range. */
  List<LibraryEvent> findByStatusAndStartsAtBetweenOrderByStartsAt(
      EventStatus status, Instant from, Instant to);

  /**
   * The event, locked until the transaction ends: places are counted and given out one sign-up at a
   * time, so two members can't both take the last place (step 332).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT e FROM LibraryEvent e WHERE e.id = :id")
  Optional<LibraryEvent> findByIdForUpdate(@Param("id") UUID id);

  /** Day-before reminders: published events starting in [from, to). */
  List<LibraryEvent> findByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
      EventStatus status, Instant from, Instant to);

  /** Staff list: every event, newest start first. */
  Page<LibraryEvent> findAllByOrderByStartsAtDesc(Pageable pageable);
}
