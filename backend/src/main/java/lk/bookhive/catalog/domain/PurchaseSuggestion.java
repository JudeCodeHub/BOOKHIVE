package lk.bookhive.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.exception.FieldValidationException;

/** A member's "please buy this book" (table {@code purchase_suggestions}, CAT-11). */
@Entity
@Table(name = "purchase_suggestions")
public class PurchaseSuggestion extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID userId;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(length = 200)
  private String author;

  @Column(length = 13)
  private String isbn13;

  @Column(length = 1000)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SuggestionStatus status;

  @Column(length = 1000)
  private String staffNote;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "book_id")
  private Book book;

  private UUID decidedBy;

  private Instant decidedAt;

  protected PurchaseSuggestion() {
    // for JPA
  }

  public PurchaseSuggestion(
      UUID userId, String title, String author, String isbn13, String reason) {
    this.userId = userId;
    this.title = title.strip();
    this.author = blankToNull(author);
    this.isbn13 = isbn13;
    this.reason = blankToNull(reason);
    this.status = SuggestionStatus.SUBMITTED;
  }

  /**
   * Staff answer the suggestion. ACQUIRED needs the book it became, so the member can find it.
   *
   * @param book required for ACQUIRED, ignored otherwise
   */
  public void decide(
      SuggestionStatus newStatus, String note, Book book, UUID staffId, Instant now) {
    if (newStatus == SuggestionStatus.ACQUIRED && book == null) {
      throw new FieldValidationException(
          "bookId", "SUGGESTION_BOOK_REQUIRED", "Choose the book it was catalogued as.");
    }
    this.status = newStatus;
    this.staffNote = blankToNull(note);
    this.book = newStatus == SuggestionStatus.ACQUIRED ? book : null;
    this.decidedBy = newStatus == SuggestionStatus.SUBMITTED ? null : staffId;
    this.decidedAt = newStatus == SuggestionStatus.SUBMITTED ? null : now;
  }

  public boolean isOpen() {
    return status == SuggestionStatus.SUBMITTED || status == SuggestionStatus.ORDERED;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTitle() {
    return title;
  }

  public String getAuthor() {
    return author;
  }

  public String getIsbn13() {
    return isbn13;
  }

  public String getReason() {
    return reason;
  }

  public SuggestionStatus getStatus() {
    return status;
  }

  public String getStaffNote() {
    return staffNote;
  }

  public Book getBook() {
    return book;
  }

  public UUID getDecidedBy() {
    return decidedBy;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }
}
