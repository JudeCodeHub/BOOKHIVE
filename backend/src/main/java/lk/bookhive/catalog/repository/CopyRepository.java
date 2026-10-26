package lk.bookhive.catalog.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.catalog.domain.Copy;
import lk.bookhive.catalog.domain.CopyStatus;
import lk.bookhive.catalog.domain.LendingType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CopyRepository extends JpaRepository<Copy, UUID> {

  /** The desk scans a barcode: the copy with its book, in one query. */
  @EntityGraph(attributePaths = "book")
  Optional<Copy> findByBarcode(String barcode);

  boolean existsByBarcode(String barcode);

  /** Just the id: doesn't load the copy, so a later locked read gets fresh data. */
  @Query("SELECT c.id FROM Copy c WHERE c.barcode = :barcode")
  Optional<UUID> findIdByBarcode(@Param("barcode") String barcode);

  /**
   * The copy, locked for the rest of the transaction (SELECT ... FOR UPDATE): another desk trying
   * to lend the same copy waits until this checkout has finished, then sees its new status.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM Copy c JOIN FETCH c.book WHERE c.id = :id")
  Optional<Copy> findByIdForUpdate(@Param("id") UUID id);

  List<Copy> findByBookIdOrderByBarcode(UUID bookId);

  /** The copies that came with a donation, with their books (donation page). */
  @EntityGraph(attributePaths = "book")
  List<Copy> findByDonationIdOrderByBarcode(UUID donationId);

  long countByDonationId(UUID donationId);

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

  /** For a page of books: how many copies each has (owned) and how many can be borrowed now. */
  interface BookCounts {
    UUID getBookId();

    long getTotal();

    long getAvailableToBorrow();
  }

  @Query(
      """
      SELECT c.book.id AS bookId,
             sum(CASE WHEN c.status NOT IN (lk.bookhive.catalog.domain.CopyStatus.LOST,
                                            lk.bookhive.catalog.domain.CopyStatus.WITHDRAWN)
                      THEN 1 ELSE 0 END) AS total,
             sum(CASE WHEN c.status = lk.bookhive.catalog.domain.CopyStatus.AVAILABLE
                       AND c.lendingType = lk.bookhive.catalog.domain.LendingType.LENDABLE
                      THEN 1 ELSE 0 END) AS availableToBorrow
      FROM Copy c WHERE c.book.id IN :bookIds
      GROUP BY c.book.id
      """)
  List<BookCounts> countsForBooks(@Param("bookIds") Collection<UUID> bookIds);

  /** Next value of the copy barcode sequence (V13). */
  @Query(value = "SELECT nextval('copy_barcode_seq')", nativeQuery = true)
  long nextBarcodeNumber();
}
