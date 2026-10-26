package lk.bookhive.catalog.service;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lk.bookhive.catalog.domain.Copy;
import lk.bookhive.catalog.domain.CopyStatus;
import lk.bookhive.catalog.domain.LendingType;
import lk.bookhive.catalog.repository.CopyRepository;
import org.springframework.stereotype.Service;
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
