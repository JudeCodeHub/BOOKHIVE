package lk.bookhive.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.common.web.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
   * What to look for in the audit log (step 359); every part is optional.
   *
   * @param action exact ("fine.waived"), or a whole area when it ends with a dot ("fine.")
   * @param from first day (library time, Sri Lanka), inclusive
   * @param to last day, inclusive
   */
  public record AuditFilter(
      UUID actorId,
      String action,
      String entityType,
      String entityId,
      LocalDate from,
      LocalDate to) {}

  /** Entries matching the filter, newest first. */
  @Transactional(readOnly = true)
  public Page<AuditLog> search(AuditFilter filter, int page, int size) {
    return auditLogs.findAll(
        specification(filter),
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
  }

  /** Every kind of action recorded so far ("auth.login_failed", "fine.waived"...). */
  @Transactional(readOnly = true)
  public List<String> actions() {
    return auditLogs.findDistinctActions();
  }

  private static Specification<AuditLog> specification(AuditFilter f) {
    List<Specification<AuditLog>> parts = new ArrayList<>();
    if (f.actorId() != null) {
      parts.add((root, query, cb) -> cb.equal(root.get("actorId"), f.actorId()));
    }
    if (f.action() != null && !f.action().isBlank()) {
      String action = f.action().strip();
      parts.add(
          action.endsWith(".")
              ? (root, query, cb) -> cb.like(root.get("action"), likePrefix(action), '\\')
              : (root, query, cb) -> cb.equal(root.get("action"), action));
    }
    if (f.entityType() != null && !f.entityType().isBlank()) {
      parts.add((root, query, cb) -> cb.equal(root.get("entityType"), f.entityType().strip()));
    }
    if (f.entityId() != null && !f.entityId().isBlank()) {
      parts.add((root, query, cb) -> cb.equal(root.get("entityId"), f.entityId().strip()));
    }
    if (f.from() != null) {
      Instant start = f.from().atStartOfDay(LibraryTime.ZONE).toInstant();
      parts.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), start));
    }
    if (f.to() != null) {
      Instant end = f.to().plusDays(1).atStartOfDay(LibraryTime.ZONE).toInstant();
      parts.add((root, query, cb) -> cb.lessThan(root.get("occurredAt"), end));
    }
    return Specification.allOf(parts);
  }

  /** "fine." -> "fine.%", with LIKE's own wildcards (% _) taken literally. */
  private static String likePrefix(String prefix) {
    return prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
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
