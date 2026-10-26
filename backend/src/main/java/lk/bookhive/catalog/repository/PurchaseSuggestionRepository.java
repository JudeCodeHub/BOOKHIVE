package lk.bookhive.catalog.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.domain.PurchaseSuggestion;
import lk.bookhive.catalog.domain.SuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseSuggestionRepository extends JpaRepository<PurchaseSuggestion, UUID> {

  /** A member's own suggestions, newest first, with the book for acquired ones. */
  @EntityGraph(attributePaths = "book")
  List<PurchaseSuggestion> findByUserIdOrderByCreatedAtDesc(UUID userId);

  long countByUserIdAndStatusIn(UUID userId, Collection<SuggestionStatus> statuses);

  /** Has this member already asked for this ISBN (and it's still open)? */
  boolean existsByUserIdAndIsbn13AndStatusIn(
      UUID userId, String isbn13, Collection<SuggestionStatus> statuses);

  /** Staff list, filtered by status. */
  @EntityGraph(attributePaths = "book")
  Page<PurchaseSuggestion> findByStatus(SuggestionStatus status, Pageable pageable);

  /** How many members asked for the same ISBN: popular requests matter more. */
  long countByIsbn13(String isbn13);
}
