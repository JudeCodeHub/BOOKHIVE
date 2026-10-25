package lk.bookhive.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.catalog.domain.Copy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CopyRepository extends JpaRepository<Copy, UUID> {

  /** The desk scans a barcode: the copy with its book, in one query. */
  @EntityGraph(attributePaths = "book")
  Optional<Copy> findByBarcode(String barcode);

  boolean existsByBarcode(String barcode);

  List<Copy> findByBookIdOrderByBarcode(UUID bookId);

  /** For label sheets: the copies with their books, in barcode order. */
  @EntityGraph(attributePaths = "book")
  List<Copy> findByIdInOrderByBarcode(List<UUID> ids);

  /** Next value of the copy barcode sequence (V13). */
  @Query(value = "SELECT nextval('copy_barcode_seq')", nativeQuery = true)
  long nextBarcodeNumber();
}
