package lk.bookhive.common.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Write through {@link AuditService}; the admin query API comes in checklist step 359. */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

  List<AuditLog> findByActionOrderByOccurredAtDesc(String action);

  List<AuditLog> findByActorIdOrderByOccurredAtDesc(UUID actorId);
}
