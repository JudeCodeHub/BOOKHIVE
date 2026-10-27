package lk.bookhive.fine.repository;

import java.util.UUID;
import lk.bookhive.fine.domain.FineTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-only use: ledger lines are written through {@code Fine} and never changed. */
public interface FineTransactionRepository extends JpaRepository<FineTransaction, UUID> {

  /** Is this cash receipt number already used? (Receipt numbers are unique.) */
  boolean existsByReference(String reference);
}
