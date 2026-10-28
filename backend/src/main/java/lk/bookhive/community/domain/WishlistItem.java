package lk.bookhive.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A book on a member's wishlist (table {@code wishlist_items}, checklist step 338). */
@Entity
@Table(name = "wishlist_items")
public class WishlistItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID memberId;

  @Column(nullable = false, updatable = false)
  private UUID bookId;

  @Column(nullable = false, updatable = false)
  private Instant addedAt;

  protected WishlistItem() {
    // for JPA
  }

  public WishlistItem(UUID memberId, UUID bookId, Instant now) {
    this.memberId = memberId;
    this.bookId = bookId;
    this.addedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public UUID getBookId() {
    return bookId;
  }

  public Instant getAddedAt() {
    return addedAt;
  }
}
