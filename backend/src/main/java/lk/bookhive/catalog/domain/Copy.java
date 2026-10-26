package lk.bookhive.catalog.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.domain.Money;
import lk.bookhive.common.exception.ConflictException;

/**
 * One physical item of a book (table {@code copies}, ADR 0003): what the desk scans and lends.
 * Status changes go through methods named after what happens ({@link #withdraw}); loans and holds
 * add theirs in the circulation phase.
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "donation_id")
  private Donation donation;

  // Written through moveTo() only; saved together with the copy. Never read through here
  // (CopyStatusChangeRepository lists it), so it stays unloaded.
  @OneToMany(mappedBy = "copy", cascade = CascadeType.PERSIST)
  private List<CopyStatusChange> history = new ArrayList<>();

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

  /** A new copy goes straight onto the shelf; that's the first line of its history. */
  public Copy(Book book, String barcode, Details details, UUID addedBy, Instant now) {
    this.book = book;
    this.barcode = barcode;
    update(details);
    moveTo(CopyStatus.AVAILABLE, "Added to the catalog", addedBy, now);
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

  /**
   * Marks the copy as part of a donation (or unlinks it with null). A donated copy's source is
   * DONATION.
   */
  public void linkDonation(Donation donation) {
    this.donation = donation;
    if (donation != null) {
      this.acquisitionSource = AcquisitionSource.DONATION;
    }
  }

  /**
   * Takes the copy out of the collection for good (damaged beyond repair, outdated, lost and
   * written off). Not while someone has it or it's waiting for a member: that would break their
   * loan or hold (checklist step 165). The row stays, so loan and fine history remain correct.
   */
  public void withdraw(String reason, UUID staffId, Instant now) {
    if (status == CopyStatus.WITHDRAWN) {
      throw new ConflictException("COPY_ALREADY_WITHDRAWN", "This copy is already withdrawn.");
    }
    if (status == CopyStatus.ON_LOAN || status == CopyStatus.ON_HOLD_SHELF) {
      throw new ConflictException(
          "COPY_IN_USE",
          status == CopyStatus.ON_LOAN
              ? "This copy is on loan. Check it in first."
              : "This copy is waiting on the hold shelf for a member.");
    }
    moveTo(CopyStatus.WITHDRAWN, reason, staffId, now);
  }

  /**
   * Lent at the desk (checklist step 221). From the shelf, or from the hold shelf for its member.
   */
  public void lend(UUID staffId, Instant now) {
    if (status != CopyStatus.AVAILABLE && status != CopyStatus.ON_HOLD_SHELF) {
      throw new ConflictException("COPY_NOT_AVAILABLE", "This copy isn't available to lend.");
    }
    moveTo(CopyStatus.ON_LOAN, "Checked out", staffId, now);
  }

  /**
   * Back from a loan (checklist step 223): onto the shelf, or onto the hold shelf when a member is
   * waiting for this title. Also when a "lost" copy turns up again (step 230).
   */
  public void checkIn(boolean toHoldShelf, UUID staffId, Instant now) {
    if (status != CopyStatus.ON_LOAN && status != CopyStatus.LOST) {
      throw new ConflictException("COPY_NOT_ON_LOAN", "This copy isn't out on loan.");
    }
    moveTo(
        toHoldShelf ? CopyStatus.ON_HOLD_SHELF : CopyStatus.AVAILABLE,
        toHoldShelf ? "Returned, held for a member" : "Returned",
        staffId,
        now);
  }

  /** The borrower lost it (checklist step 229). It may still turn up: see {@link #checkIn}. */
  public void markLost(UUID staffId, Instant now) {
    if (status != CopyStatus.ON_LOAN) {
      throw new ConflictException("COPY_NOT_ON_LOAN", "This copy isn't out on loan.");
    }
    moveTo(CopyStatus.LOST, "Declared lost by the borrower", staffId, now);
  }

  /**
   * Damaged on a loan (checklist step 229). Its condition becomes DAMAGED; if it's back in the
   * library and not set aside for a member, it goes for repair instead of onto the shelf.
   */
  public void markDamaged(String note, UUID staffId, Instant now) {
    this.condition = CopyCondition.DAMAGED;
    if (status == CopyStatus.AVAILABLE || status == CopyStatus.IN_TRANSIT) {
      moveTo(CopyStatus.IN_REPAIR, "Damaged: " + note, staffId, now);
    }
  }

  /**
   * The one place where the status changes (CAT-9, checklist step 166): every change also adds a
   * line to the copy's history, so no code path can forget to record it. Loans and holds will
   * change the status through methods that call this too.
   *
   * @param actorId who did it; null for automatic jobs
   */
  void moveTo(CopyStatus next, String reason, UUID actorId, Instant now) {
    history.add(new CopyStatusChange(this, status, next, reason, actorId, now));
    this.status = next;
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

  public Donation getDonation() {
    return donation;
  }
}
