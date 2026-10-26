package lk.bookhive.catalog.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.domain.Copy;
import lk.bookhive.catalog.domain.CopyStatus;
import lk.bookhive.catalog.domain.LendingType;
import lk.bookhive.catalog.repository.BookRepository;
import lk.bookhive.catalog.repository.CopyRepository;
import lk.bookhive.common.domain.Money;
import lk.bookhive.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** What circulation needs to know about a copy before lending (checklist step 220). */
@Service
public class LendingCatalogService {

  private final CopyRepository copies;

  private final BookRepository books;

  public LendingCatalogService(CopyRepository copies, BookRepository books) {
    this.copies = copies;
    this.books = books;
  }

  /**
   * A title as the hold rules see it (step 251).
   *
   * @param lendableCopies copies the library has that may go home (not reference-only, lost or
   *     withdrawn): with none, there is nothing to wait for
   * @param availableToBorrow of those, on the shelf right now
   * @param sections the library sections of its categories (no category = ADULT)
   */
  public record HoldableTitle(
      UUID bookId,
      String title,
      Set<String> sections,
      long lendableCopies,
      long availableToBorrow) {}

  @Transactional(readOnly = true)
  public Optional<HoldableTitle> holdableTitle(UUID bookId) {
    return books
        .findById(bookId)
        .map(
            book -> {
              long lendable = 0;
              long available = 0;
              for (CopyRepository.StatusCount row : copies.countByStatus(bookId)) {
                if (row.getLendingType() != LendingType.LENDABLE
                    || row.getStatus() == CopyStatus.LOST
                    || row.getStatus() == CopyStatus.WITHDRAWN) {
                  continue;
                }
                lendable += row.getCount();
                if (row.getStatus() == CopyStatus.AVAILABLE) {
                  available += row.getCount();
                }
              }
              return new HoldableTitle(
                  book.getId(), book.getTitle(), sections(book), lendable, available);
            });
  }

  /**
   * A copy as circulation sees it.
   *
   * @param sections the library sections of the book's categories (ADULT, CHILDREN...); a book with
   *     no category counts as ADULT
   */
  public record LendableCopy(
      UUID copyId,
      String barcode,
      UUID bookId,
      String title,
      CopyStatus status,
      LendingType lendingType,
      Set<String> sections,
      String shelfLocation,
      String callNumber,
      Money price,
      String coverVersion) {}

  /** The desk scans the label: "C0000123". */
  @Transactional(readOnly = true)
  public Optional<LendableCopy> byBarcode(String barcode) {
    return copies
        .findByBarcode(barcode.strip().toUpperCase(Locale.ROOT))
        .map(LendingCatalogService::lendable);
  }

  @Transactional(readOnly = true)
  public Optional<LendableCopy> byId(UUID copyId) {
    return copies.findById(copyId).map(LendingCatalogService::lendable);
  }

  /** Several copies at once (loan lists), by id. Unknown ids are left out. */
  @Transactional(readOnly = true)
  public Map<UUID, LendableCopy> byIds(Collection<UUID> copyIds) {
    return copies.findAllById(copyIds).stream()
        .map(LendingCatalogService::lendable)
        .collect(Collectors.toMap(LendableCopy::copyId, Function.identity()));
  }

  /**
   * The copy's id for a barcode, without loading the copy. Checkout uses this before locking: a
   * copy already loaded in the transaction would be returned as it was, not re-read under lock.
   */
  @Transactional(readOnly = true)
  public Optional<UUID> idByBarcode(String barcode) {
    return copies.findIdByBarcode(barcode.strip().toUpperCase(Locale.ROOT));
  }

  /**
   * Locks the copy until the caller's transaction ends and returns its current state (checklist
   * step 221). Must be called inside a transaction (the checkout's).
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public LendableCopy lockForLending(UUID copyId) {
    return copies
        .findByIdForUpdate(copyId)
        .map(LendingCatalogService::lendable)
        .orElseThrow(() -> new NotFoundException("COPY_NOT_FOUND", "Copy not found."));
  }

  /** Marks the copy as on loan (and writes its history line). Inside the checkout transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lend(UUID copyId, UUID staffId, Instant now) {
    copies
        .findById(copyId)
        .orElseThrow(() -> new NotFoundException("COPY_NOT_FOUND", "Copy not found."))
        .lend(staffId, now);
  }

  /** Back from a loan: to the shelf or the hold shelf. Inside the return transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void checkIn(UUID copyId, boolean toHoldShelf, UUID staffId, Instant now) {
    copies
        .findById(copyId)
        .orElseThrow(() -> new NotFoundException("COPY_NOT_FOUND", "Copy not found."))
        .checkIn(toHoldShelf, staffId, now);
  }

  /** Lost by its borrower (step 229). Inside the caller's transaction. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void markLost(UUID copyId, UUID staffId, Instant now) {
    copies
        .findById(copyId)
        .orElseThrow(() -> new NotFoundException("COPY_NOT_FOUND", "Copy not found."))
        .markLost(staffId, now);
  }

  /** Damaged on a loan (step 229): condition DAMAGED, off to repair if it's back. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void markDamaged(UUID copyId, String note, UUID staffId, Instant now) {
    copies
        .findById(copyId)
        .orElseThrow(() -> new NotFoundException("COPY_NOT_FOUND", "Copy not found."))
        .markDamaged(note, staffId, now);
  }

  private static LendableCopy lendable(Copy copy) {
    Set<String> sections = sections(copy.getBook());
    return new LendableCopy(
        copy.getId(),
        copy.getBarcode(),
        copy.getBook().getId(),
        copy.getBook().getTitle(),
        copy.getStatus(),
        copy.getLendingType(),
        sections,
        copy.getShelfLocation(),
        copy.getBook().getCallNumber(),
        copy.getPrice(),
        CoverService.version(copy.getBook()));
  }

  /** The sections of a book's categories; a book with no category counts as ADULT. */
  private static Set<String> sections(Book book) {
    Set<String> sections =
        book.getCategories().stream()
            .map(category -> category.getSection().name())
            .collect(Collectors.toSet());
    return sections.isEmpty() ? Set.of("ADULT") : Set.copyOf(sections);
  }
}
