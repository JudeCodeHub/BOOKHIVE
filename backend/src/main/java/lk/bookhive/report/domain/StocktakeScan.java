package lk.bookhive.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One barcode scanned during a stock-take (table {@code stocktake_scans}). Never changed. */
@Entity
@Table(name = "stocktake_scans")
public class StocktakeScan {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID sessionId;

  @Column(nullable = false, length = 30)
  private String barcode;

  private UUID copyId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ScanResult result;

  @Column(length = 20)
  private String copyStatus;

  @Column(nullable = false)
  private UUID scannedBy;

  @Column(nullable = false)
  private Instant scannedAt;

  protected StocktakeScan() {
    // for JPA
  }

  public static StocktakeScan of(
      UUID sessionId,
      String barcode,
      UUID copyId,
      ScanResult result,
      String copyStatus,
      UUID staffId,
      Instant now) {
    StocktakeScan scan = new StocktakeScan();
    scan.sessionId = sessionId;
    scan.barcode = barcode;
    scan.copyId = copyId;
    scan.result = result;
    scan.copyStatus = copyStatus;
    scan.scannedBy = staffId;
    scan.scannedAt = now;
    return scan;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public String getBarcode() {
    return barcode;
  }

  public UUID getCopyId() {
    return copyId;
  }

  public ScanResult getResult() {
    return result;
  }

  public String getCopyStatus() {
    return copyStatus;
  }

  public Instant getScannedAt() {
    return scannedAt;
  }
}
