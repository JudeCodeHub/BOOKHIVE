package lk.bookhive.report.repository;

import java.util.List;
import java.util.UUID;
import lk.bookhive.report.domain.ScanResult;
import lk.bookhive.report.domain.StocktakeScan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StocktakeScanRepository extends JpaRepository<StocktakeScan, UUID> {

  boolean existsBySessionIdAndCopyId(UUID sessionId, UUID copyId);

  boolean existsBySessionIdAndBarcode(UUID sessionId, String barcode);

  long countBySessionId(UUID sessionId);

  long countBySessionIdAndResult(UUID sessionId, ScanResult result);

  /** Scans that need a look (not FOUND), newest first. */
  List<StocktakeScan> findBySessionIdAndResultNotOrderByScannedAtDesc(
      UUID sessionId, ScanResult result);
}
