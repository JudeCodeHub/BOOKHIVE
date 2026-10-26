package lk.bookhive.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One group of library-wide settings, stored as JSON (table {@code library_settings}). Read and
 * written only through SettingsService, which turns the JSON into a typed record.
 */
@Entity
@Table(name = "library_settings")
@EntityListeners(AuditingEntityListener.class)
public class LibrarySetting {

  @Id
  @Column(length = 50)
  private String key;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String value;

  private UUID updatedBy;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  // Two admins saving at once: the second gets a conflict instead of silently overwriting
  @Version
  @Column(nullable = false)
  private Long version;

  protected LibrarySetting() {
    // for JPA
  }

  public void change(String json, UUID adminId) {
    this.value = json;
    this.updatedBy = adminId;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
