package lk.bookhive.catalog.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lk.bookhive.catalog.domain.Copy;
import lk.bookhive.catalog.domain.CopyStatus;
import lk.bookhive.catalog.domain.LendingType;
import lk.bookhive.catalog.repository.CopyRepository;
import lk.bookhive.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** What circulation needs to know about a copy before lending (checklist step 220). */
@Service
public class LendingCatalogService {

  private final CopyRepository copies;

  public LendingCatalogService(CopyRepository copies) {
    this.copies = copies;
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
      Set<String> sections) {}

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

  private static LendableCopy lendable(Copy copy) {
    Set<String> sections =
        copy.getBook().getCategories().stream()
            .map(category -> category.getSection().name())
            .collect(Collectors.toSet());
    return new LendableCopy(
        copy.getId(),
        copy.getBarcode(),
        copy.getBook().getId(),
        copy.getBook().getTitle(),
        copy.getStatus(),
        copy.getLendingType(),
        sections.isEmpty() ? Set.of("ADULT") : Set.copyOf(sections));
  }
}
