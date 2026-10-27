package lk.bookhive.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.bookhive.notification.domain.Channel;
import lk.bookhive.notification.domain.OutboxMessage;
import lk.bookhive.notification.domain.OutboxStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

  /** Already queued? (Same dedupe key and channel = the same reminder.) */
  boolean existsByDedupeKeyAndChannel(String dedupeKey, Channel channel);

  /**
   * The ids of messages due to be sent now, oldest first (dispatcher, step 308). {@code SKIP
   * LOCKED}: if two app instances run the dispatcher, each takes different rows instead of waiting
   * for (or sending) the same ones.
   */
  @Query(
      value =
          """
          SELECT id FROM outbox_messages
          WHERE status = 'PENDING' AND next_attempt_at <= :now
          ORDER BY next_attempt_at
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<UUID> lockDue(@Param("now") Instant now, @Param("limit") int limit);

  /** Admin view (step 319): messages in a status, newest change first. */
  Page<OutboxMessage> findByStatusOrderByUpdatedAtDesc(OutboxStatus status, Pageable pageable);
}
