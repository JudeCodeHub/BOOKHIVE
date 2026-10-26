package lk.bookhive.catalog.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.domain.Copy;
import lk.bookhive.catalog.domain.CopyStatusChange;
import lk.bookhive.catalog.repository.BookRepository;
import lk.bookhive.catalog.repository.CopyRepository;
import lk.bookhive.catalog.repository.CopyStatusChangeRepository;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adding, editing and withdrawing physical copies (CAT-2, checklist steps 159-160, 165). */
@Service
public class CopyService {

  /** Most copies bought of one title at once; also keeps a mistyped "500" from flooding labels. */
  public static final int MAX_COPIES_AT_ONCE = 50;

  private final CopyRepository copies;
  private final BookRepository books;
  private final CopyStatusChangeRepository history;
  private final AuditService audit;
  private final LibraryTime time;

  public CopyService(
      CopyRepository copies,
      BookRepository books,
      CopyStatusChangeRepository history,
      AuditService audit,
      LibraryTime time) {
    this.copies = copies;
    this.books = books;
    this.history = history;
    this.audit = audit;
    this.time = time;
  }

  /** Where a copy has been, newest first (CAT-9). */
  @Transactional(readOnly = true)
  public List<CopyStatusChange> history(UUID copyId) {
    require(copyId);
    return history.findByCopyIdOrderByChangedAtDesc(copyId);
  }

  @Transactional(readOnly = true)
  public List<Copy> forBook(UUID bookId) {
    requireBook(bookId);
    return copies.findByBookIdOrderByBarcode(bookId);
  }

  /**
   * Adds {@code count} copies with the same details. Barcodes are generated (C0000123), unless
   * staff give one: then exactly one copy is added with that barcode (an existing label).
   */
  @Transactional
  public List<Copy> add(
      UUID bookId, Copy.Details details, String barcode, int count, UUID staffId) {
    Book book = requireBook(bookId);
    List<Copy> added = new ArrayList<>();
    if (barcode != null && !barcode.isBlank()) {
      String normalised = barcode.strip().toUpperCase(Locale.ROOT);
      if (count != 1) {
        throw new FieldValidationException(
            "count", "ONE_COPY_PER_BARCODE", "With your own barcode, add one copy at a time.");
      }
      if (copies.existsByBarcode(normalised)) {
        throw new FieldValidationException(
            "barcode", "BARCODE_EXISTS", "Another copy already has this barcode.");
      }
      added.add(copies.save(new Copy(book, normalised, details, staffId, time.now())));
    } else {
      for (int i = 0; i < count; i++) {
        added.add(copies.save(new Copy(book, nextBarcode(), details, staffId, time.now())));
      }
    }
    Map<String, Object> audited = new HashMap<>();
    audited.put("bookId", bookId.toString());
    audited.put("barcodes", added.stream().map(Copy::getBarcode).toList());
    audit.record("copy.added", staffId, "book", bookId, audited);
    return added;
  }

  @Transactional
  public Copy update(UUID copyId, Copy.Details details, UUID staffId) {
    Copy copy = require(copyId);
    copy.update(details);
    audit.record("copy.updated", staffId, "copy", copyId, Map.of("barcode", copy.getBarcode()));
    return copy;
  }

  /** The reason is required; it goes into the copy's history and the audit log. */
  @Transactional
  public Copy withdraw(UUID copyId, String reason, UUID staffId) {
    Copy copy = require(copyId);
    copy.withdraw(reason.strip(), staffId, time.now());
    audit.record(
        "copy.withdrawn",
        staffId,
        "copy",
        copyId,
        Map.of("barcode", copy.getBarcode(), "reason", reason.strip()));
    return copy;
  }

  /** C0000001 ... C9999999, then it grows a digit. */
  private String nextBarcode() {
    return "C" + String.format("%07d", copies.nextBarcodeNumber());
  }

  private Book requireBook(UUID bookId) {
    return books
        .findById(bookId)
        .orElseThrow(() -> new NotFoundException("BOOK_NOT_FOUND", "Book not found."));
  }

  private Copy require(UUID copyId) {
    return copies
        .findById(copyId)
        .orElseThrow(() -> new NotFoundException("COPY_NOT_FOUND", "Copy not found."));
  }

  /** Exposed for the label sheet: the chosen copies, or every copy of one book. */
  @Transactional(readOnly = true)
  public List<Copy> forLabels(List<UUID> copyIds, UUID bookId) {
    if (copyIds != null && !copyIds.isEmpty()) {
      return copies.findByIdInOrderByBarcode(copyIds);
    }
    if (bookId != null) {
      return copies.findByIdInOrderByBarcode(
          copies.findByBookIdOrderByBarcode(bookId).stream().map(Copy::getId).toList());
    }
    throw new FieldValidationException(
        "ids", "NO_COPIES_CHOSEN", "Choose the copies to print labels for.");
  }
}
