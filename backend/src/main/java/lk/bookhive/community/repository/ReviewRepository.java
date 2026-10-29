package lk.bookhive.community.repository;

import java.util.Optional;
import java.util.UUID;
import lk.bookhive.community.domain.Review;
import lk.bookhive.community.domain.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

  /** A book's reviews in a status, newest first (APPROVED for the website). */
  Page<Review> findByBookIdAndStatusOrderByCreatedAtDesc(
      UUID bookId, ReviewStatus status, Pageable pageable);

  /** The member's review of a book (one at most). */
  Optional<Review> findByMemberIdAndBookId(UUID memberId, UUID bookId);

  /** The moderation queue: oldest first. */
  Page<Review> findByStatusOrderByUpdatedAtAsc(ReviewStatus status, Pageable pageable);

  long countByStatus(ReviewStatus status);

  /** Average rating and count of a book's approved reviews. */
  interface RatingSummary {
    Double getAverage();

    long getCount();
  }

  @Query(
      "SELECT avg(r.rating) AS average, count(r) AS count FROM Review r"
          + " WHERE r.bookId = :bookId AND r.status = lk.bookhive.community.domain.ReviewStatus.APPROVED")
  RatingSummary ratingOf(@Param("bookId") UUID bookId);
}
