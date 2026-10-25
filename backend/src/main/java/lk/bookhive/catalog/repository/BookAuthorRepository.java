package lk.bookhive.catalog.repository;

import java.util.UUID;
import lk.bookhive.catalog.domain.BookAuthor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookAuthorRepository extends JpaRepository<BookAuthor, UUID> {

  /** Is this author on any book? (Then they can't be deleted.) */
  boolean existsByAuthorId(UUID authorId);

  long countByAuthorId(UUID authorId);
}
