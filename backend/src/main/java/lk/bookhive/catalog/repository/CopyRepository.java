package lk.bookhive.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.catalog.domain.Copy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CopyRepository extends JpaRepository<Copy, UUID> {

  /** The desk scans a barcode: the copy with its book, in one query. */
  @EntityGraph(attributePaths = "book")
  Optional<Copy> findByBarcode(String barcode);

  boolean existsByBarcode(String barcode);

  List<Copy> findByBookIdOrderByBarcode(UUID bookId);
}
