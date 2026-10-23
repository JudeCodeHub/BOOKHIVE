package lk.bookhive.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.common.web.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes audit entries. Part of the caller's transaction: if the action is rolled back, so is its
 * audit entry, so the log never claims something happened that didn't.
 *
 * <pre>
 * audit.record("fine.waived", userId, "fine", fineId, Map.of("reason", reason));
 * </pre>
 *
 * <p>The client IP and request id are filled in automatically from the current HTTP request.
 */
@Service
public class AuditService {

  private final AuditLogRepository auditLogs;
  private final Clock clock;

  public AuditService(AuditLogRepository auditLogs, Clock clock) {
    this.auditLogs = auditLogs;
    this.clock = clock;
  }

  /**
   * @param action what happened, "module.verb_in_past_tense", e.g. "auth.password_changed"
   * @param actorId who did it; null if anonymous
   * @param entityType / entityId what it was done to; may be null
   * @param details extra facts; never put passwords, tokens or other secrets in here
   */
  @Transactional
  public void record(
      String action,
      UUID actorId,
      String entityType,
      Object entityId,
      Map<String, Object> details) {
    auditLogs.save(
        new AuditLog(
            Instant.now(clock),
            actorId,
            action,
            entityType,
            entityId == null ? null : entityId.toString(),
            details == null ? Map.of() : details,
            currentClientIp(),
            MDC.get(RequestIdFilter.MDC_KEY)));
  }

  private static String currentClientIp() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      HttpServletRequest request = attrs.getRequest();
      return request.getRemoteAddr();
    }
    return null; // e.g. a scheduled job: no HTTP request
  }
}
