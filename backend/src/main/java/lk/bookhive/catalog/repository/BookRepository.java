package lk.bookhive.catalog.repository;

import java.util.Optional;
import java.util.UUID;
import lk.bookhive.catalog.domain.Book;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, UUID> {

  /**
   * A book with everything a book page shows, in one query instead of one per author: the
   * contributors with their authors, the publisher and the categories. Contributors is the only
   * list (a "bag"), so Hibernate can fetch it together with the categories set.
   */
  @EntityGraph(attributePaths = {"contributors", "contributors.author", "publisher", "categories"})
  Optional<Book> findWithDetailsById(UUID id);

  /** Cataloguing: scanning an ISBN finds the existing title instead of making a duplicate. */
  Optional<Book> findByIsbn13(String isbn13);

  Optional<Book> findByIsbn10(String isbn10);
}
