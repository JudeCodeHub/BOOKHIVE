package lk.bookhive.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

  /** The whole tree in one query (a few hundred rows at most); the caller builds the nesting. */
  List<Category> findAllByOrderByDdcCodeAscNameEnAsc();

  /** Import: the category with this Dewey number (the first, if several share it). */
  Optional<Category> findFirstByDdcCode(String ddcCode);
}
