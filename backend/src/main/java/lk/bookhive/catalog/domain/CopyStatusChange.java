package lk.bookhive.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One line of a copy's history (table {@code copy_status_history}, CAT-9). Written only by {@link
 * Copy} when its status changes; never edited (the database refuses updates too).
 */
@Entity
@Table(name = "copy_status_history")
@Immutable
public class CopyStatusChange {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "copy_id", nullable = false, updatable = false)
  private Copy copy;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, updatable = false)
  private CopyStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private CopyStatus toStatus;

  @Column(length = 500, updatable = false)
  private String reason;

  @Column(updatable = false)
  private UUID changedBy;

  @Column(nullable = false, updatable = false)
  private Instant changedAt;

  protected CopyStatusChange() {
    // for JPA
  }

  CopyStatusChange(
      Copy copy,
      CopyStatus fromStatus,
      CopyStatus toStatus,
      String reason,
      UUID changedBy,
      Instant changedAt) {
    this.copy = copy;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.reason = reason;
    this.changedBy = changedBy;
    this.changedAt = changedAt;
  }

  public UUID getId() {
    return id;
  }

  public CopyStatus getFromStatus() {
    return fromStatus;
  }

  public CopyStatus getToStatus() {
    return toStatus;
  }

  public String getReason() {
    return reason;
  }

  public UUID getChangedBy() {
    return changedBy;
  }

  public Instant getChangedAt() {
    return changedAt;
  }
}
