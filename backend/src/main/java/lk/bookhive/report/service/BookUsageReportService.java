package lk.bookhive.report.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.report.repository.BookUsageReportQueries;
import lk.bookhive.report.repository.BookUsageReportQueries.MostBorrowedRow;
import lk.bookhive.report.repository.BookUsageReportQueries.NotBorrowedRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Most borrowed and the weeding list (RPT-3, checklist step 362): what readers love, and books that
 * have sat on the shelf unborrowed (candidates for removal, a display, or a second look).
 */
@Service
public class BookUsageReportService {

  /** The weeding list looks back this far unless asked otherwise. */
  public static final int DEFAULT_UNBORROWED_YEARS = 2;

  public record NotBorrowedPage(
      LocalDate since, List<NotBorrowedRow> books, int page, int size, long total) {}

  private final BookUsageReportQueries queries;
  private final LibraryTime time;

  public BookUsageReportService(BookUsageReportQueries queries, LibraryTime time) {
    this.queries = queries;
    this.time = time;
  }

  @Transactional(readOnly = true)
  public List<MostBorrowedRow> mostBorrowed(
      LocalDate from, LocalDate to, UUID categoryId, int limit) {
    CirculationReportService.checkRange(from, to, CirculationReportService.MAX_DAYS_OTHERWISE);
    return queries.mostBorrowed(
        from.atStartOfDay(LibraryTime.ZONE).toInstant(),
        to.plusDays(1).atStartOfDay(LibraryTime.ZONE).toInstant(),
        categoryId,
        limit);
  }

  /**
   * @param since null = {@value #DEFAULT_UNBORROWED_YEARS} years ago
   */
  @Transactional(readOnly = true)
  public NotBorrowedPage notBorrowedSince(LocalDate since, UUID categoryId, int page, int size) {
    LocalDate day = since != null ? since : time.today().minusYears(DEFAULT_UNBORROWED_YEARS);
    if (!day.isBefore(time.today())) {
      throw new FieldValidationException("since", "DATE_NOT_PAST", "Choose a day in the past.");
    }
    Instant start = day.atStartOfDay(LibraryTime.ZONE).toInstant();
    return new NotBorrowedPage(
        day,
        queries.notBorrowedSince(start, day, categoryId, size, page * size),
        page,
        size,
        queries.countNotBorrowedSince(start, day, categoryId));
  }
}
