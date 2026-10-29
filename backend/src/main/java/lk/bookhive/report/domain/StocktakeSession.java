package lk.bookhive.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.exception.ConflictException;

/** One stock-take (table {@code stocktake_sessions}). */
@Entity
@Table(name = "stocktake_sessions")
public class StocktakeSession extends BaseEntity {

  @Column(nullable = false, length = 120)
  private String name;

  private UUID categoryId;

  @Column(length = 100)
  private String shelfPrefix;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private StocktakeStatus status;

  @Column(nullable = false)
  private UUID startedBy;

  @Column(nullable = false)
  private Instant startedAt;

  private UUID finishedBy;

  private Instant finishedAt;

  private Integer expectedCount;

  private Integer foundCount;

  private Integer missingCount;

  protected StocktakeSession() {
    // for JPA
  }

  public static StocktakeSession start(
      String name, UUID categoryId, String shelfPrefix, UUID staffId, Instant now) {
    StocktakeSession session = new StocktakeSession();
    session.name = name;
    session.categoryId = categoryId;
    session.shelfPrefix = shelfPrefix;
    session.status = StocktakeStatus.OPEN;
    session.startedBy = staffId;
    session.startedAt = now;
    return session;
  }

  /** Scanning is done: the counts are kept as they are now. */
  public void complete(int expected, int found, int missing, UUID staffId, Instant now) {
    requireOpen();
    this.status = StocktakeStatus.COMPLETED;
    this.expectedCount = expected;
    this.foundCount = found;
    this.missingCount = missing;
    this.finishedBy = staffId;
    this.finishedAt = now;
  }

  public void cancel(UUID staffId, Instant now) {
    requireOpen();
    this.status = StocktakeStatus.CANCELLED;
    this.finishedBy = staffId;
    this.finishedAt = now;
  }

  public void requireOpen() {
    if (status != StocktakeStatus.OPEN) {
      throw new ConflictException("STOCKTAKE_CLOSED", "This stock-take has already finished.");
    }
  }

  public String getName() {
    return name;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getShelfPrefix() {
    return shelfPrefix;
  }

  public StocktakeStatus getStatus() {
    return status;
  }

  public UUID getStartedBy() {
    return startedBy;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public UUID getFinishedBy() {
    return finishedBy;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public Integer getExpectedCount() {
    return expectedCount;
  }

  public Integer getFoundCount() {
    return foundCount;
  }

  public Integer getMissingCount() {
    return missingCount;
  }
}
