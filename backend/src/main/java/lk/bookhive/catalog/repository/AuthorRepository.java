package lk.bookhive.catalog.repository;

import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.domain.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthorRepository extends JpaRepository<Author, UUID> {

  /** Staff author list: part of the name, A–Z (paging and sorting come from the Pageable). */
  Page<Author> findByNameContainingIgnoreCase(String namePart, Pageable pageable);

  /**
   * Authors whose name looks like the text typed, best match first (trigram similarity), so a small
   * typo still finds them. For the "author" picker when cataloguing.
   */
  @Query(
      value =
          """
          SELECT * FROM authors
          WHERE name ILIKE '%' || :text || '%' OR similarity(name, :text) > 0.3
          ORDER BY similarity(name, :text) DESC, name
          LIMIT :limit
          """,
      nativeQuery = true)
  List<Author> suggest(@Param("text") String text, @Param("limit") int limit);
}
