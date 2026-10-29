package lk.bookhive.common.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Write through {@link AuditService}; admins search it through AuditService.search (step 359). */
public interface AuditLogRepository
    extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

  List<AuditLog> findByActionOrderByOccurredAtDesc(String action);

  List<AuditLog> findByActorIdOrderByOccurredAtDesc(UUID actorId);

  /** Every kind of action recorded so far, for the audit page's filter. */
  @Query("select distinct a.action from AuditLog a order by a.action")
  List<String> findDistinctActions();
}
