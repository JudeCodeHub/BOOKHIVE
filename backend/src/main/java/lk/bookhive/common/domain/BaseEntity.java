package lk.bookhive.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Fields every table has: a UUID primary key, audit timestamps and a version for optimistic
 * locking. Columns map to {@code id, created_at, updated_at, version}.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  // Incremented on every update; a stale update fails instead of overwriting someone else's change.
  @Version
  @Column(nullable = false)
  private Long version;

  public UUID getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Long getVersion() {
    return version;
  }

  // Equal when same entity type and same id. Unsaved entities (id == null) are only equal to
  // themselves. Hibernate.getClass unwraps lazy-loading proxies.
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
      return false;
    }
    BaseEntity other = (BaseEntity) o;
    return id != null && Objects.equals(id, other.id);
  }

  // Constant per type so the hash doesn't change when the id is assigned on save.
  @Override
  public int hashCode() {
    return Hibernate.getClass(this).hashCode();
  }
}
