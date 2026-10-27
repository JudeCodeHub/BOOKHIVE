package lk.bookhive.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One message for the bell (table {@code notifications}, NOTIF-3, step 312). Only facts are stored;
 * the text is written when it's read, in the language the site is used in.
 */
@Entity
@Table(name = "notifications")
public class InAppNotification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40, updatable = false)
  private NotificationType type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private Map<String, String> params;

  @Column(length = 300, updatable = false)
  private String link;

  @Column(length = 200, updatable = false)
  private String dedupeKey;

  private Instant readAt;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected InAppNotification() {
    // for JPA
  }

  public InAppNotification(
      UUID userId,
      NotificationType type,
      Map<String, String> params,
      String link,
      String dedupeKey,
      Instant now) {
    this.userId = userId;
    this.type = type;
    this.params = Map.copyOf(params);
    this.link = link;
    this.dedupeKey = dedupeKey;
    this.createdAt = now;
  }

  public void markRead(Instant now) {
    if (readAt == null) {
      this.readAt = now;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public NotificationType getType() {
    return type;
  }

  public Map<String, String> getParams() {
    return params;
  }

  public String getLink() {
    return link;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
