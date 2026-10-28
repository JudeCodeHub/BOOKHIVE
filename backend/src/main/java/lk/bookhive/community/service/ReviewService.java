package lk.bookhive.community.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.circulation.service.LoanLookupService;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.community.domain.Review;
import lk.bookhive.community.domain.ReviewStatus;
import lk.bookhive.community.repository.ReviewRepository;
import lk.bookhive.member.service.BorrowerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Book reviews (COMM-1, checklist step 337). Only members who have borrowed the book may write one;
 * it appears on the website after staff approve it. Reviewers are shown by first name and initial
 * only.
 */
@Service
public class ReviewService {

  /** A review with what screens show next to it. */
  public record ReviewView(Review review, String reviewerName, String bookTitle) {}

  /**
   * @param average null when there are no approved reviews
   */
  public record Rating(Double average, long count) {}

  /**
   * What the member can do on a book page.
   *
   * @param mine their review (any status), if they wrote one
   */
  public record MyReview(boolean canReview, Optional<Review> mine) {}

  private final ReviewRepository reviews;
  private final LoanLookupService loans;
  private final BorrowerService borrowers;
  private final AuthService authService;
  private final LendingCatalogService catalog;
  private final AuditService audit;
  private final LibraryTime time;

  public ReviewService(
      ReviewRepository reviews,
      LoanLookupService loans,
      BorrowerService borrowers,
      AuthService authService,
      LendingCatalogService catalog,
      AuditService audit,
      LibraryTime time) {
    this.reviews = reviews;
    this.loans = loans;
    this.borrowers = borrowers;
    this.authService = authService;
    this.catalog = catalog;
    this.audit = audit;
    this.time = time;
  }

  /** A book's approved reviews, newest first. */
  @Transactional(readOnly = true)
  public Page<ReviewView> approved(UUID bookId, int page, int size) {
    return reviews
        .findByBookIdAndStatusOrderByCreatedAtDesc(
            bookId, ReviewStatus.APPROVED, PageRequest.of(page, size))
        .map(review -> new ReviewView(review, publicName(review.getMemberId()), null));
  }

  @Transactional(readOnly = true)
  public Rating rating(UUID bookId) {
    var summary = reviews.ratingOf(bookId);
    Double average =
        summary.getAverage() == null ? null : Math.round(summary.getAverage() * 10) / 10.0;
    return new Rating(average, summary.getCount());
  }

  @Transactional(readOnly = true)
  public MyReview mine(UUID bookId, UUID memberId) {
    return new MyReview(
        loans.hasEverBorrowed(memberId, bookId), reviews.findByMemberIdAndBookId(memberId, bookId));
  }

  /**
   * Writes the member's review, or changes it; either way it waits for staff again.
   *
   * @throws BusinessRuleException NOT_BORROWED when they never borrowed the book
   */
  @Transactional
  public Review write(UUID bookId, UUID memberId, int rating, String text) {
    if (rating < 1 || rating > 5) {
      throw new FieldValidationException("rating", "RATING_OUT_OF_RANGE", "Choose 1 to 5 stars.");
    }
    if (catalog.holdableTitle(bookId).isEmpty()) {
      throw new NotFoundException("BOOK_NOT_FOUND", "Book not found.");
    }
    if (!loans.hasEverBorrowed(memberId, bookId)) {
      throw new BusinessRuleException(
          "NOT_BORROWED", "You can review a book once you've borrowed it from the library.");
    }
    Review review =
        reviews
            .findByMemberIdAndBookId(memberId, bookId)
            .map(
                existing -> {
                  existing.edit(rating, text);
                  return existing;
                })
            .orElseGet(() -> reviews.save(Review.write(bookId, memberId, rating, text)));
    return review;
  }

  @Transactional
  public void delete(UUID bookId, UUID memberId) {
    Review review =
        reviews
            .findByMemberIdAndBookId(memberId, bookId)
            .orElseThrow(
                () -> new NotFoundException("REVIEW_NOT_FOUND", "You have no review here."));
    reviews.delete(review);
  }

  /** The moderation queue: oldest first, with the book title and the reviewer's name. */
  @Transactional(readOnly = true)
  public Page<ReviewView> pending(int page, int size) {
    return reviews
        .findByStatusOrderByUpdatedAtAsc(ReviewStatus.PENDING, PageRequest.of(page, size))
        .map(
            review ->
                new ReviewView(
                    review,
                    fullName(review.getMemberId()),
                    catalog.holdableTitle(review.getBookId()).map(t -> t.title()).orElse("")));
  }

  @Transactional
  public Review approve(UUID reviewId, UUID staffId) {
    Review review = require(reviewId);
    review.approve(staffId, time.now());
    audit.record("review.approved", staffId, "review", reviewId, Map.of());
    return review;
  }

  @Transactional
  public Review reject(UUID reviewId, String reason, UUID staffId) {
    Review review = require(reviewId);
    review.reject(reason, staffId, time.now());
    audit.record("review.rejected", staffId, "review", reviewId, Map.of("reason", reason.strip()));
    return review;
  }

  /** "Nimali Perera" -> "Nimali P.": enough to recognise a review, not to find the person. */
  private String publicName(UUID memberId) {
    String full = fullName(memberId);
    String[] parts = full.strip().split("\\s+");
    if (parts.length < 2) {
      return parts[0];
    }
    return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
  }

  private String fullName(UUID memberId) {
    return borrowers
        .byId(memberId)
        .map(b -> authService.getProfile(b.userId()).getFullName())
        .orElse("A reader");
  }

  private Review require(UUID id) {
    return reviews
        .findById(id)
        .orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "Review not found."));
  }
}
