package lk.bookhive.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.catalog.domain.Copy;
import lk.bookhive.catalog.domain.CopyStatus;
import lk.bookhive.catalog.domain.LendingType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CopyRepository extends JpaRepository<Copy, UUID> {

  /** The desk scans a barcode: the copy with its book, in one query. */
  @EntityGraph(attributePaths = "book")
  Optional<Copy> findByBarcode(String barcode);

  boolean existsByBarcode(String barcode);

  List<Copy> findByBookIdOrderByBarcode(UUID bookId);

  /** For label sheets: the copies with their books, in barcode order. */
  @EntityGraph(attributePaths = "book")
  List<Copy> findByIdInOrderByBarcode(List<UUID> ids);

  /** One row of the availability count: how many copies have this status and lending type. */
  interface StatusCount {
    CopyStatus getStatus();

    LendingType getLendingType();

    long getCount();
  }

  /** Copies of a book counted by status and lending type, in one query. */
  @Query(
      """
      SELECT c.status AS status, c.lendingType AS lendingType, count(c) AS count
      FROM Copy c WHERE c.book.id = :bookId
      GROUP BY c.status, c.lendingType
      """)
  List<StatusCount> countByStatus(@Param("bookId") UUID bookId);

  /** Where the copies on the shelf right now can be found ("Adult fiction, shelf 12"). */
  @Query(
      """
      SELECT DISTINCT c.shelfLocation FROM Copy c
      WHERE c.book.id = :bookId AND c.status = lk.bookhive.catalog.domain.CopyStatus.AVAILABLE
        AND c.shelfLocation IS NOT NULL
      ORDER BY c.shelfLocation
      """)
  List<String> shelvesWithAvailableCopies(@Param("bookId") UUID bookId);

  /** Next value of the copy barcode sequence (V13). */
  @Query(value = "SELECT nextval('copy_barcode_seq')", nativeQuery = true)
  long nextBarcodeNumber();
}
