package lk.bookhive.community.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.community.domain.Review;
import lk.bookhive.community.domain.ReviewStatus;
import lk.bookhive.community.service.ReviewService;
import lk.bookhive.community.service.ReviewService.ReviewView;
import lk.bookhive.member.service.BorrowerService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Book reviews (COMM-1, step 337): approved reviews and the average rating for everyone; writing
 * one for members who borrowed the book; moderation for staff.
 */
@RestController
@Tag(name = "Reviews")
public class ReviewController {

  private final ReviewService reviews;
  private final BorrowerService borrowers;

  public ReviewController(ReviewService reviews, BorrowerService borrowers) {
    this.reviews = reviews;
    this.borrowers = borrowers;
  }

  /**
   * An approved review on the book page.
   *
   * @param reviewer first name and initial only
   */
  public record ReviewSummary(
      UUID id,
      int rating,
      @Schema(nullable = true) String text,
      String reviewer,
      Instant createdAt) {

    static ReviewSummary from(ReviewView view) {
      Review r = view.review();
      return new ReviewSummary(
          r.getId(), r.getRating(), r.getText(), view.reviewerName(), r.getCreatedAt());
    }
  }

  /**
   * @param average out of 5, one decimal; null when there are none yet
   */
  public record BookReviewsResponse(
      @Schema(nullable = true) Double average, long count, PageResponse<ReviewSummary> reviews) {}

  /**
   * The member's own review.
   *
   * @param rejectReason why staff didn't publish it (they can edit and send it again)
   */
  public record MyReviewSummary(
      UUID id,
      int rating,
      String text,
      ReviewStatus status,
      @Schema(nullable = true) String rejectReason,
      Instant updatedAt) {

    static MyReviewSummary from(Review r) {
      return new MyReviewSummary(
          r.getId(),
          r.getRating(),
          r.getText(),
          r.getStatus(),
          r.getRejectReason(),
          r.getUpdatedAt());
    }
  }

  /**
   * @param canReview they borrowed this book, so they may review it
   * @param review their review, if they wrote one
   */
  public record MyReviewResponse(
      boolean canReview, @Schema(nullable = true) MyReviewSummary review) {}

  public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 2000) String text) {}

  public record RejectReviewRequest(@NotBlank @Size(min = 5, max = 500) String reason) {}

  /** A review waiting for staff, with the book and the reviewer's full name. */
  public record PendingReviewSummary(
      UUID id,
      UUID bookId,
      String bookTitle,
      String reviewer,
      int rating,
      String text,
      Instant updatedAt) {

    static PendingReviewSummary from(ReviewView view) {
      Review r = view.review();
      return new PendingReviewSummary(
          r.getId(),
          r.getBookId(),
          view.bookTitle(),
          view.reviewerName(),
          r.getRating(),
          r.getText(),
          r.getUpdatedAt());
    }
  }

  @GetMapping("/books/{bookId}/reviews")
  @Operation(summary = "A book's approved reviews (newest first) and its average rating")
  public BookReviewsResponse bookReviews(
      @PathVariable UUID bookId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
    var rating = reviews.rating(bookId);
    return new BookReviewsResponse(
        rating.average(),
        rating.count(),
        PageResponse.from(reviews.approved(bookId, page, size), ReviewSummary::from));
  }

  @GetMapping("/me/reviews/{bookId}")
  @SecurityRequirement(name = OpenApiConfig.BEARER)
  @Tag(name = "My reviews")
  @Operation(summary = "Can I review this book, and my review if I wrote one")
  public MyReviewResponse mine(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookId) {
    var mine = reviews.mine(bookId, memberId(jwt));
    return new MyReviewResponse(
        mine.canReview(), mine.mine().map(MyReviewSummary::from).orElse(null));
  }

  @PutMapping("/me/reviews/{bookId}")
  @SecurityRequirement(name = OpenApiConfig.BEARER)
  @Tag(name = "My reviews")
  @Operation(summary = "Write or change my review (it waits for staff before it shows)")
  public MyReviewSummary write(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID bookId,
      @Valid @RequestBody ReviewRequest request) {
    return MyReviewSummary.from(
        reviews.write(bookId, memberId(jwt), request.rating(), request.text()));
  }

  @DeleteMapping("/me/reviews/{bookId}")
  @SecurityRequirement(name = OpenApiConfig.BEARER)
  @Tag(name = "My reviews")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete my review")
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookId) {
    reviews.delete(bookId, memberId(jwt));
  }

  @GetMapping("/staff/reviews/pending")
  @SecurityRequirement(name = OpenApiConfig.BEARER)
  @Tag(name = "Staff: reviews")
  @Operation(summary = "Reviews waiting for moderation, oldest first")
  public PageResponse<PendingReviewSummary> pending(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(reviews.pending(page, size), PendingReviewSummary::from);
  }

  @PostMapping("/staff/reviews/{id}/approve")
  @SecurityRequirement(name = OpenApiConfig.BEARER)
  @Tag(name = "Staff: reviews")
  @Operation(summary = "Approve a review: it appears on the book page")
  public MyReviewSummary approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return MyReviewSummary.from(reviews.approve(id, UUID.fromString(jwt.getSubject())));
  }

  @PostMapping("/staff/reviews/{id}/reject")
  @SecurityRequirement(name = OpenApiConfig.BEARER)
  @Tag(name = "Staff: reviews")
  @Operation(summary = "Reject a review with a reason the member sees")
  public MyReviewSummary reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody RejectReviewRequest request) {
    return MyReviewSummary.from(
        reviews.reject(id, request.reason(), UUID.fromString(jwt.getSubject())));
  }

  private UUID memberId(Jwt jwt) {
    return borrowers
        .byUserId(UUID.fromString(jwt.getSubject()))
        .orElseThrow(
            () -> new NotFoundException("MEMBER_NOT_FOUND", "You are not a library member yet."))
        .memberId();
  }
}
