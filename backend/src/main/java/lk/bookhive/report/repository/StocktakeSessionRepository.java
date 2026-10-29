package lk.bookhive.report.repository;

import java.util.UUID;
import lk.bookhive.report.domain.StocktakeSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StocktakeSessionRepository extends JpaRepository<StocktakeSession, UUID> {

  Page<StocktakeSession> findAllByOrderByStartedAtDesc(Pageable pageable);
}
