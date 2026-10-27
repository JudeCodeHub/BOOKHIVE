package lk.bookhive.notification.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.notification.domain.InAppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

  Page<InAppNotification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  long countByUserIdAndReadAtIsNull(UUID userId);

  Optional<InAppNotification> findByIdAndUserId(UUID id, UUID userId);

  boolean existsByUserIdAndDedupeKey(UUID userId, String dedupeKey);

  /** "Mark all as read" in one statement. */
  @Modifying
  @Query(
      "UPDATE InAppNotification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
  int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
