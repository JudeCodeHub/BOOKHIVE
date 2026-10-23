package lk.bookhive.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One audit entry. Immutable: created once by {@link AuditService}, never changed (the database
 * rejects updates too). Not a BaseEntity: it has no updated_at or version.
 */
@Entity
@Table(name = "audit_logs")
@Immutable
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, updatable = false)
  private Instant occurredAt;

  private UUID actorId;

  @Column(nullable = false, length = 60)
  private String action;

  @Column(length = 60)
  private String entityType;

  @Column(length = 100)
  private String entityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> details;

  @Column(length = 45)
  private String ip;

  @Column(length = 64)
  private String requestId;

  /** For JPA only. */
  protected AuditLog() {}

  AuditLog(
      Instant occurredAt,
      UUID actorId,
      String action,
      String entityType,
      String entityId,
      Map<String, Object> details,
      String ip,
      String requestId) {
    this.occurredAt = occurredAt;
    this.actorId = actorId;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.details = Map.copyOf(details);
    this.ip = ip;
    this.requestId = requestId;
  }

  public UUID getId() {
    return id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getAction() {
    return action;
  }

  public String getEntityType() {
    return entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public Map<String, Object> getDetails() {
    return details;
  }

  public String getIp() {
    return ip;
  }

  public String getRequestId() {
    return requestId;
  }
}
