package lk.bookhive.catalog.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.domain.BookAuthor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookAuthorRepository extends JpaRepository<BookAuthor, UUID> {

  /** Is this author on any book? (Then they can't be deleted.) */
  boolean existsByAuthorId(UUID authorId);

  long countByAuthorId(UUID authorId);

  /** The contributors of a whole page of books in one query (no query per book). */
  @EntityGraph(attributePaths = "author")
  List<BookAuthor> findByBookIdInOrderByPosition(Collection<UUID> bookIds);
}
