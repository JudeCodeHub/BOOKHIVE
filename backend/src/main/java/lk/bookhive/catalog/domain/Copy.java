package lk.bookhive.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.domain.Money;

/**
 * One physical item of a book (table {@code copies}, ADR 0003): what the desk scans and lends.
 * Status changes go through methods added with the copy rules (steps 159, 165, 166).
 */
@Entity
@Table(name = "copies")
public class Copy extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "book_id", nullable = false, updatable = false)
  private Book book;

  @Column(nullable = false, length = 30, unique = true)
  private String barcode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CopyStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LendingType lendingType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CopyCondition condition;

  @Column(length = 100)
  private String shelfLocation;

  private LocalDate acquiredAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AcquisitionSource acquisitionSource;

  @Column(precision = 10, scale = 2)
  private BigDecimal price;

  @Column(length = 500)
  private String notes;

  protected Copy() {
    // for JPA
  }

  /** What staff fill in for a copy. */
  public record Details(
      LendingType lendingType,
      CopyCondition condition,
      String shelfLocation,
      LocalDate acquiredAt,
      AcquisitionSource acquisitionSource,
      Money price,
      String notes) {}

  /** A new copy goes straight onto the shelf. */
  public Copy(Book book, String barcode, Details details) {
    this.book = book;
    this.barcode = barcode;
    this.status = CopyStatus.AVAILABLE;
    update(details);
  }

  public void update(Details details) {
    this.lendingType = details.lendingType();
    this.condition = details.condition();
    this.shelfLocation = blankToNull(details.shelfLocation());
    this.acquiredAt = details.acquiredAt();
    this.acquisitionSource = details.acquisitionSource();
    this.price = details.price() == null ? null : details.price().amount();
    this.notes = blankToNull(details.notes());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  public Book getBook() {
    return book;
  }

  public String getBarcode() {
    return barcode;
  }

  public CopyStatus getStatus() {
    return status;
  }

  public LendingType getLendingType() {
    return lendingType;
  }

  public CopyCondition getCondition() {
    return condition;
  }

  public String getShelfLocation() {
    return shelfLocation;
  }

  public LocalDate getAcquiredAt() {
    return acquiredAt;
  }

  public AcquisitionSource getAcquisitionSource() {
    return acquisitionSource;
  }

  public Money getPrice() {
    return price == null ? null : new Money(price);
  }

  public String getNotes() {
    return notes;
  }
}
