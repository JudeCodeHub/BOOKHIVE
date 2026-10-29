package lk.bookhive.hold.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.exception.ConflictException;

/**
 * A member's place in the queue for a title (table {@code holds}, checklist step 250). The book,
 * member, copy and loan are referenced by id: they belong to other modules.
 *
 * <p>State changes go through methods named after what happens; the rules (limits, who is next, the
 * pickup deadline) are in the hold services.
 */
@Entity
@Table(name = "holds")
public class Hold extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID bookId;

  @Column(nullable = false, updatable = false)
  private UUID memberId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private HoldStatus status;

  @Column(nullable = false, updatable = false)
  private Instant placedAt;

  @Column(updatable = false)
  private UUID placedBy;

  private UUID assignedCopyId;

  private Instant readyAt;

  /** The last library day to pick it up. */
  private LocalDate expiresAt;

  private Instant closedAt;

  private UUID closedBy;

  @Column(length = 500)
  private String cancelReason;

  private UUID loanId;

  protected Hold() {
    // for JPA
  }

  /**
   * Joins the queue for a title.
   *
   * @param placedBy staff placing it for the member at the desk; null when the member did
   */
  public static Hold place(UUID bookId, UUID memberId, UUID placedBy, Instant now) {
    Hold hold = new Hold();
    hold.bookId = bookId;
    hold.memberId = memberId;
    hold.placedBy = placedBy;
    hold.placedAt = now;
    hold.status = HoldStatus.WAITING;
    return hold;
  }

  /** A copy came back and is set aside for this member until the end of {@code pickUpBy}. */
  public void makeReady(UUID copyId, Instant now, LocalDate pickUpBy) {
    if (status != HoldStatus.WAITING) {
      throw new ConflictException("HOLD_NOT_WAITING", "This hold isn't waiting for a copy.");
    }
    this.status = HoldStatus.READY;
    this.assignedCopyId = copyId;
    this.readyAt = now;
    this.expiresAt = pickUpBy;
  }

  /**
   * The library is now closed on the last pickup day (step 357): the copy waits until the next open
   * day instead.
   */
  public void extendPickup(LocalDate nextOpenDay) {
    if (status == HoldStatus.READY) {
      this.expiresAt = nextOpenDay;
    }
  }

  /** The member borrowed the copy set aside for them. */
  public void fulfil(UUID loanId, Instant now) {
    if (status != HoldStatus.READY) {
      throw new ConflictException("HOLD_NOT_READY", "This hold has no copy waiting.");
    }
    this.status = HoldStatus.FULFILLED;
    this.loanId = loanId;
    this.closedAt = now;
  }

  /**
   * The member or staff cancel it.
   *
   * @return the copy that was set aside (now free for the next in line), or null if it was waiting
   */
  public UUID cancel(UUID actorId, String reason, Instant now) {
    requireOpen();
    UUID released = status == HoldStatus.READY ? assignedCopyId : null;
    this.status = HoldStatus.CANCELLED;
    this.closedBy = actorId;
    this.cancelReason = reason;
    this.closedAt = now;
    return released;
  }

  /**
   * Not picked up in time (nightly job, step 255).
   *
   * @return the copy that was set aside, now free for the next in line
   */
  public UUID expire(Instant now) {
    if (status != HoldStatus.READY) {
      throw new ConflictException("HOLD_NOT_READY", "This hold has no copy waiting.");
    }
    this.status = HoldStatus.EXPIRED;
    this.closedAt = now;
    return assignedCopyId;
  }

  /** Past its pickup day on this library date. */
  public boolean isExpiredOn(LocalDate today) {
    return status == HoldStatus.READY && expiresAt.isBefore(today);
  }

  private void requireOpen() {
    if (!status.isOpen()) {
      throw new ConflictException("HOLD_NOT_OPEN", "This hold is already closed.");
    }
  }

  public UUID getBookId() {
    return bookId;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public HoldStatus getStatus() {
    return status;
  }

  public Instant getPlacedAt() {
    return placedAt;
  }

  public UUID getPlacedBy() {
    return placedBy;
  }

  public UUID getAssignedCopyId() {
    return assignedCopyId;
  }

  public Instant getReadyAt() {
    return readyAt;
  }

  public LocalDate getExpiresAt() {
    return expiresAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public UUID getClosedBy() {
    return closedBy;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  public UUID getLoanId() {
    return loanId;
  }
}
