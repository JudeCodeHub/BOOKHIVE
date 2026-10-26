package lk.bookhive.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;

/** Books someone gave the library (table {@code donations}, CAT-10). Copies point to it. */
@Entity
@Table(name = "donations")
public class Donation extends BaseEntity {

  @Column(nullable = false, length = 200)
  private String donorName;

  @Column(length = 200)
  private String donorContact;

  @Column(nullable = false)
  private LocalDate receivedAt;

  @Column(length = 1000)
  private String notes;

  @Column(updatable = false)
  private UUID recordedBy;

  protected Donation() {
    // for JPA
  }

  public Donation(
      String donorName, String donorContact, LocalDate receivedAt, String notes, UUID recordedBy) {
    update(donorName, donorContact, receivedAt, notes);
    this.recordedBy = recordedBy;
  }

  public void update(String donorName, String donorContact, LocalDate receivedAt, String notes) {
    this.donorName = donorName.strip();
    this.donorContact = blankToNull(donorContact);
    this.receivedAt = receivedAt;
    this.notes = blankToNull(notes);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  public String getDonorName() {
    return donorName;
  }

  public String getDonorContact() {
    return donorContact;
  }

  public LocalDate getReceivedAt() {
    return receivedAt;
  }

  public String getNotes() {
    return notes;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }
}
