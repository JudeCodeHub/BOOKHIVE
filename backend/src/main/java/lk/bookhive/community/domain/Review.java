package lk.bookhive.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.exception.ConflictException;

/**
 * A member's review of a book (table {@code reviews}, COMM-1, checklist step 337). Moderated by
 * staff before it's shown; editing sends it back to moderation.
 */
@Entity
@Table(name = "reviews")
public class Review extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID bookId;

  @Column(nullable = false, updatable = false)
  private UUID memberId;

  @Column(nullable = false)
  private short rating;

  @Column(nullable = false, length = 2000)
  private String text;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReviewStatus status;

  @Column(length = 500)
  private String rejectReason;

  private UUID moderatedBy;

  private Instant moderatedAt;

  protected Review() {
    // for JPA
  }

  public static Review write(UUID bookId, UUID memberId, int rating, String text) {
    Review review = new Review();
    review.bookId = bookId;
    review.memberId = memberId;
    review.edit(rating, text);
    return review;
  }

  /** New words or rating: back to staff before it shows again. */
  public void edit(int rating, String text) {
    this.rating = (short) rating;
    this.text = text == null ? "" : text.strip();
    this.status = ReviewStatus.PENDING;
    this.rejectReason = null;
    this.moderatedBy = null;
    this.moderatedAt = null;
  }

  public void approve(UUID staffId, Instant now) {
    requirePending();
    this.status = ReviewStatus.APPROVED;
    this.moderatedBy = staffId;
    this.moderatedAt = now;
  }

  /** Not shown; the member sees why and can edit it. */
  public void reject(String reason, UUID staffId, Instant now) {
    requirePending();
    this.status = ReviewStatus.REJECTED;
    this.rejectReason = reason.strip();
    this.moderatedBy = staffId;
    this.moderatedAt = now;
  }

  private void requirePending() {
    if (status != ReviewStatus.PENDING) {
      throw new ConflictException("REVIEW_NOT_PENDING", "This review was already moderated.");
    }
  }

  public UUID getBookId() {
    return bookId;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public int getRating() {
    return rating;
  }

  public String getText() {
    return text;
  }

  public ReviewStatus getStatus() {
    return status;
  }

  public String getRejectReason() {
    return rejectReason;
  }

  public UUID getModeratedBy() {
    return moderatedBy;
  }

  public Instant getModeratedAt() {
    return moderatedAt;
  }
}
