package lk.bookhive.catalog.repository;

import java.util.Optional;
import java.util.UUID;
import lk.bookhive.catalog.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, UUID> {

  /** Cataloguing: scanning an ISBN finds the existing title instead of making a duplicate. */
  Optional<Book> findByIsbn13(String isbn13);

  Optional<Book> findByIsbn10(String isbn10);
}
