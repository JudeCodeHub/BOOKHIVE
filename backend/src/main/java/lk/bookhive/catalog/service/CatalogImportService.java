package lk.bookhive.catalog.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lk.bookhive.catalog.domain.AcquisitionSource;
import lk.bookhive.catalog.domain.Author;
import lk.bookhive.catalog.domain.AuthorRole;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.domain.BookFormat;
import lk.bookhive.catalog.domain.Category;
import lk.bookhive.catalog.domain.Copy;
import lk.bookhive.catalog.domain.CopyCondition;
import lk.bookhive.catalog.domain.Isbn;
import lk.bookhive.catalog.domain.LendingType;
import lk.bookhive.catalog.domain.Publisher;
import lk.bookhive.catalog.repository.AuthorRepository;
import lk.bookhive.catalog.repository.BookRepository;
import lk.bookhive.catalog.repository.CategoryRepository;
import lk.bookhive.catalog.repository.PublisherRepository;
import lk.bookhive.catalog.service.BookService.BookForm;
import lk.bookhive.catalog.service.BookService.ContributorForm;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.domain.Money;
import lk.bookhive.common.exception.BookHiveException;
import lk.bookhive.common.exception.FieldValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bulk catalog import from a CSV file (CAT-8, checklist step 168), e.g. a spreadsheet of a new book
 * delivery or the old card catalogue typed up.
 *
 * <p>Each row is saved in its own transaction: a bad row is reported and skipped, the others are
 * kept. A row whose ISBN is already in the catalog adds copies to that book instead of a duplicate.
 * Authors and publishers are matched by name and created if new.
 */
@Service
public class CatalogImportService {

  public static final int MAX_ROWS = 2000;

  /** The columns, in the order of the template file. Only title and language are required. */
  public static final List<String> COLUMNS =
      List.of(
          "isbn",
          "title",
          "subtitle",
          "authors",
          "publisher",
          "publish_year",
          "pages",
          "language",
          "format",
          "call_number",
          "categories",
          "copies",
          "shelf_location",
          "price",
          "acquisition_source");

  /** What happened to one row. {@code line} is the line in the file (the header is line 1). */
  public enum RowOutcome {
    CREATED,
    COPIES_ADDED,
    FAILED
  }

  public record RowResult(
      long line, RowOutcome outcome, UUID bookId, String title, int copiesAdded, String error) {}

  /**
   * @param dryRun true = a preview: every row was checked and then rolled back, nothing is saved
   */
  public record ImportReport(
      boolean dryRun,
      int rows,
      int created,
      int copiesAdded,
      int failed,
      List<RowResult> results) {}

  private final BookService bookService;
  private final CopyService copyService;
  private final BookRepository books;
  private final AuthorRepository authors;
  private final PublisherRepository publishers;
  private final CategoryRepository categories;
  private final AuditService audit;
  private final TransactionTemplate rowTransaction;

  public CatalogImportService(
      BookService bookService,
      CopyService copyService,
      BookRepository books,
      AuthorRepository authors,
      PublisherRepository publishers,
      CategoryRepository categories,
      AuditService audit,
      PlatformTransactionManager transactions) {
    this.bookService = bookService;
    this.copyService = copyService;
    this.books = books;
    this.authors = authors;
    this.publishers = publishers;
    this.categories = categories;
    this.audit = audit;
    this.rowTransaction = new TransactionTemplate(transactions);
    // A new transaction per row, so one failure rolls back only that row
    this.rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * @param dryRun preview only (checklist step 180): each row runs exactly like a real import, then
   *     its transaction is rolled back, so the report shows what would happen. (Generated barcodes
   *     are used up even then; gaps in barcode numbers are harmless.)
   */
  public ImportReport importCsv(InputStream content, UUID staffId, boolean dryRun)
      throws IOException {
    List<RowResult> results = new ArrayList<>();
    try (Reader reader = withoutBom(content);
        CSVParser parser =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get()
                .parse(reader)) {
      checkHeader(parser.getHeaderNames());
      for (CSVRecord record : parser) {
        if (record.getRecordNumber() > MAX_ROWS) {
          throw new FieldValidationException(
              "file", "IMPORT_TOO_MANY_ROWS", "Import at most " + MAX_ROWS + " rows at a time.");
        }
        results.add(importRow(record, staffId, dryRun));
      }
    }
    ImportReport report = summarise(results, dryRun);
    if (dryRun) {
      return report;
    }
    audit.record(
        "catalog.imported",
        staffId,
        "catalog",
        null,
        Map.of(
            "rows", report.rows(),
            "created", report.created(),
            "copiesAdded", report.copiesAdded(),
            "failed", report.failed()));
    return report;
  }

  private RowResult importRow(CSVRecord record, UUID staffId, boolean dryRun) {
    long line = record.getRecordNumber() + 1;
    String title = value(record, "title");
    try {
      return rowTransaction.execute(
          status -> {
            RowResult result = saveRow(record, line, staffId);
            if (dryRun) {
              status.setRollbackOnly();
            }
            return result;
          });
    } catch (FieldValidationException e) {
      return failed(line, title, e.getField() + ": " + e.getMessage());
    } catch (BookHiveException | IllegalArgumentException e) {
      return failed(line, title, e.getMessage());
    } catch (DataIntegrityViolationException e) {
      return failed(line, title, "The database refused this row (a value is too long or invalid).");
    }
  }

  private RowResult saveRow(CSVRecord record, long line, UUID staffId) {
    String title = required(record, "title");
    String isbnText = value(record, "isbn");
    Isbn isbn = isbnText == null ? null : Isbn.parse(isbnText).orElse(null);
    if (isbnText != null && isbn == null) {
      throw new IllegalArgumentException("isbn: not a valid ISBN (" + isbnText + ")");
    }

    // Already in the catalog: just add the new copies to it
    Book existing = isbn == null ? null : books.findByIsbn13(isbn.isbn13()).orElse(null);
    Book book =
        existing != null
            ? existing
            : bookService.create(bookForm(record, title, isbnText), staffId);

    int count = integer(record, "copies", 1);
    if (count < 0 || count > CopyService.MAX_COPIES_AT_ONCE) {
      throw new IllegalArgumentException("copies: between 0 and " + CopyService.MAX_COPIES_AT_ONCE);
    }
    if (count > 0) {
      copyService.add(book.getId(), copyDetails(record), null, count, null, staffId);
    }
    return new RowResult(
        line,
        existing != null ? RowOutcome.COPIES_ADDED : RowOutcome.CREATED,
        book.getId(),
        book.getTitle(),
        count,
        null);
  }

  private BookForm bookForm(CSVRecord record, String title, String isbn) {
    List<ContributorForm> contributors = new ArrayList<>();
    for (String name : list(record, "authors")) {
      Author author =
          authors
              .findFirstByNameIgnoreCase(name)
              .orElseGet(() -> authors.save(new Author(name, null)));
      contributors.add(new ContributorForm(author.getId(), AuthorRole.AUTHOR));
    }
    String publisherName = value(record, "publisher");
    UUID publisherId =
        publisherName == null
            ? null
            : publishers
                .findByName(publisherName)
                .orElseGet(() -> publishers.save(new Publisher(publisherName)))
                .getId();
    Set<UUID> categoryIds = new LinkedHashSet<>();
    for (String ddc : list(record, "categories")) {
      Category category =
          categories
              .findFirstByDdcCode(ddc)
              .orElseThrow(
                  () -> new IllegalArgumentException("categories: no category with DDC " + ddc));
      categoryIds.add(category.getId());
    }
    Integer year = integer(record, "publish_year", null);
    return new BookForm(
        title,
        value(record, "subtitle"),
        isbn,
        null,
        year == null ? null : year.shortValue(),
        integer(record, "pages", null),
        required(record, "language").toLowerCase(Locale.ROOT),
        null,
        enumValue(record, "format", BookFormat.class, BookFormat.BOOK),
        value(record, "call_number"),
        publisherId,
        contributors,
        categoryIds);
  }

  private static Copy.Details copyDetails(CSVRecord record) {
    String price = value(record, "price");
    Money money;
    try {
      money = price == null ? null : new Money(new BigDecimal(price));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("price: not a number (" + price + ")");
    }
    return new Copy.Details(
        LendingType.LENDABLE,
        CopyCondition.NEW,
        value(record, "shelf_location"),
        null,
        enumValue(
            record, "acquisition_source", AcquisitionSource.class, AcquisitionSource.PURCHASE),
        money,
        "Imported from CSV");
  }

  // --- CSV helpers -------------------------------------------------------------------------

  private static void checkHeader(List<String> header) {
    Set<String> given = new LinkedHashSet<>();
    header.forEach(name -> given.add(name.toLowerCase(Locale.ROOT)));
    if (!given.contains("title") || !given.contains("language")) {
      throw new FieldValidationException(
          "file",
          "IMPORT_BAD_HEADER",
          "The first line must name the columns, including title and language. "
              + "Download the template to see them all.");
    }
  }

  /** Excel adds an invisible "byte order mark" at the start of UTF-8 files; skip it. */
  private static Reader withoutBom(InputStream content) throws IOException {
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(content, StandardCharsets.UTF_8));
    reader.mark(1);
    if (reader.read() != '﻿') {
      reader.reset();
    }
    return reader;
  }

  private static String value(CSVRecord record, String column) {
    if (!record.isMapped(column) || !record.isSet(column)) {
      return null;
    }
    String value = record.get(column);
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String required(CSVRecord record, String column) {
    String value = value(record, column);
    if (value == null) {
      throw new IllegalArgumentException(column + ": required");
    }
    return value;
  }

  /** "A; B; C" -> [A, B, C] */
  private static List<String> list(CSVRecord record, String column) {
    String value = value(record, column);
    return value == null
        ? List.of()
        : Arrays.stream(value.split(";")).map(String::strip).filter(s -> !s.isEmpty()).toList();
  }

  private static Integer integer(CSVRecord record, String column, Integer fallback) {
    String value = value(record, column);
    try {
      return value == null ? fallback : Integer.valueOf(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(column + ": not a whole number (" + value + ")");
    }
  }

  private static <E extends Enum<E>> E enumValue(
      CSVRecord record, String column, Class<E> type, E fallback) {
    String value = value(record, column);
    try {
      return value == null ? fallback : Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          column + ": one of " + Arrays.toString(type.getEnumConstants()) + " (" + value + ")");
    }
  }

  private static RowResult failed(long line, String title, String error) {
    return new RowResult(line, RowOutcome.FAILED, null, title, 0, error);
  }

  private static ImportReport summarise(List<RowResult> results, boolean dryRun) {
    int created = 0;
    int copies = 0;
    int failed = 0;
    for (RowResult result : results) {
      switch (result.outcome()) {
        case CREATED -> created++;
        case FAILED -> failed++;
        case COPIES_ADDED -> {}
      }
      copies += result.copiesAdded();
    }
    return new ImportReport(dryRun, results.size(), created, copies, failed, results);
  }

  /** The template file staff fill in: the header line and one example row. */
  public static String template() {
    return String.join(",", COLUMNS)
        + "\n"
        + "9789552104540,Gamperaliya,,Martin Wickramasinghe,Sarasavi Publishers,1944,250,si,BOOK,"
        + "891.483 WIC,891.48,2,\"Sinhala fiction, shelf 3\",950.00,PURCHASE\n";
  }
}
