package lk.bookhive.community.service;

import java.util.UUID;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.catalog.service.LendingCatalogService.HoldableTitle;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.community.domain.WishlistItem;
import lk.bookhive.community.repository.WishlistItemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A member's wishlist (checklist step 338): books to read later, each shown with whether a copy is
 * on the shelf right now. Adding a book that's already there changes nothing.
 */
@Service
public class WishlistService {

  /** Enough for any reader; stops a script filling the table. */
  public static final int MAX_ITEMS = 200;

  /**
   * A wishlist entry with its book.
   *
   * @param title null if the book was removed from the catalog meanwhile
   */
  public record WishlistEntry(WishlistItem item, HoldableTitle title) {}

  private final WishlistItemRepository items;
  private final LendingCatalogService catalog;
  private final LibraryTime time;

  public WishlistService(
      WishlistItemRepository items, LendingCatalogService catalog, LibraryTime time) {
    this.items = items;
    this.catalog = catalog;
    this.time = time;
  }

  @Transactional(readOnly = true)
  public Page<WishlistEntry> list(UUID memberId, int page, int size) {
    return items
        .findByMemberIdOrderByAddedAtDesc(memberId, PageRequest.of(page, size))
        .map(item -> new WishlistEntry(item, catalog.holdableTitle(item.getBookId()).orElse(null)));
  }

  @Transactional(readOnly = true)
  public boolean contains(UUID memberId, UUID bookId) {
    return items.findByMemberIdAndBookId(memberId, bookId).isPresent();
  }

  /** Saves the book (already saved: nothing changes). */
  @Transactional
  public WishlistEntry add(UUID memberId, UUID bookId) {
    HoldableTitle title =
        catalog
            .holdableTitle(bookId)
            .orElseThrow(() -> new NotFoundException("BOOK_NOT_FOUND", "Book not found."));
    var existing = items.findByMemberIdAndBookId(memberId, bookId);
    if (existing.isPresent()) {
      return new WishlistEntry(existing.get(), title);
    }
    if (items.countByMemberId(memberId) >= MAX_ITEMS) {
      throw new BusinessRuleException(
          "WISHLIST_FULL", "Your wishlist is full (" + MAX_ITEMS + " books). Remove some first.");
    }
    try {
      return new WishlistEntry(
          items.saveAndFlush(new WishlistItem(memberId, bookId, time.now())), title);
    } catch (DataIntegrityViolationException e) {
      // Saved twice at the same moment (double click): it's on the list either way
      return new WishlistEntry(
          items.findByMemberIdAndBookId(memberId, bookId).orElseThrow(), title);
    }
  }

  /** Takes the book off the list (not on it: nothing to do). */
  @Transactional
  public void remove(UUID memberId, UUID bookId) {
    items.findByMemberIdAndBookId(memberId, bookId).ifPresent(items::delete);
  }
}
