package lk.bookhive.catalog.service;

import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.domain.CopyStatus;
import lk.bookhive.catalog.domain.LendingType;
import lk.bookhive.catalog.repository.BookRepository;
import lk.bookhive.catalog.repository.CopyRepository;
import lk.bookhive.catalog.repository.CopyRepository.StatusCount;
import lk.bookhive.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "2 of 3 available, Shelf 823.91 FER" (SRCH-4, checklist step 167). Always counted from the
 * copies, never stored on the book, so it can't drift out of date (ADR 0003).
 */
@Service
public class AvailabilityService {

  private final BookRepository books;
  private final CopyRepository copies;

  public AvailabilityService(BookRepository books, CopyRepository copies) {
    this.books = books;
    this.copies = copies;
  }

  /**
   * @param total copies the library has (lost and withdrawn ones don't count)
   * @param available on the shelf now, including reference-only ones
   * @param availableToBorrow on the shelf and allowed to go home (members care about this one)
   * @param referenceOnly reference-only copies on the shelf: read them in the library
   * @param holdsWaiting members queued for this title (holds arrive with circulation; 0 until then)
   * @param shelves where the available copies are
   */
  public record Availability(
      long total,
      long available,
      long availableToBorrow,
      long referenceOnly,
      long onLoan,
      long onHoldShelf,
      long inTransit,
      long inRepair,
      long holdsWaiting,
      List<String> shelves) {}

  @Transactional(readOnly = true)
  public Availability forBook(UUID bookId) {
    if (!books.existsById(bookId)) {
      throw new NotFoundException("BOOK_NOT_FOUND", "Book not found.");
    }
    List<StatusCount> counts = copies.countByStatus(bookId);
    return new Availability(
        sum(counts, null, null, true),
        sum(counts, CopyStatus.AVAILABLE, null, false),
        sum(counts, CopyStatus.AVAILABLE, LendingType.LENDABLE, false),
        sum(counts, CopyStatus.AVAILABLE, LendingType.REFERENCE_ONLY, false),
        sum(counts, CopyStatus.ON_LOAN, null, false),
        sum(counts, CopyStatus.ON_HOLD_SHELF, null, false),
        sum(counts, CopyStatus.IN_TRANSIT, null, false),
        sum(counts, CopyStatus.IN_REPAIR, null, false),
        0,
        copies.shelvesWithAvailableCopies(bookId));
  }

  /**
   * Adds up the matching rows. {@code null} status/type = any. {@code ownedOnly} leaves out lost
   * and withdrawn copies, which the library no longer has.
   */
  private static long sum(
      List<StatusCount> counts, CopyStatus status, LendingType type, boolean ownedOnly) {
    return counts.stream()
        .filter(row -> status == null || row.getStatus() == status)
        .filter(row -> type == null || row.getLendingType() == type)
        .filter(
            row ->
                !ownedOnly
                    || (row.getStatus() != CopyStatus.LOST
                        && row.getStatus() != CopyStatus.WITHDRAWN))
        .mapToLong(StatusCount::getCount)
        .sum();
  }
}
