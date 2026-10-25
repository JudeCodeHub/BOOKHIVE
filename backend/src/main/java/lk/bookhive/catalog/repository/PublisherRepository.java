package lk.bookhive.catalog.repository;

import java.util.Optional;
import java.util.UUID;
import lk.bookhive.catalog.domain.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublisherRepository extends JpaRepository<Publisher, UUID> {

  /** Case-insensitive, because the column is citext. */
  Optional<Publisher> findByName(String name);
}
