package lk.bookhive.catalog.repository;

import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.domain.CopyStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CopyStatusChangeRepository extends JpaRepository<CopyStatusChange, UUID> {

  /** A copy's history, newest first. */
  List<CopyStatusChange> findByCopyIdOrderByChangedAtDesc(UUID copyId);
}
