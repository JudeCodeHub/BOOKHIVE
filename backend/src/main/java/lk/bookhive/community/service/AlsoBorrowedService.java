package lk.bookhive.community.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.service.BookListService.BookListItem;
import lk.bookhive.catalog.service.SearchService;
import lk.bookhive.circulation.service.LoanLookupService;
import lk.bookhive.common.cache.PublicCache;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

/**
 * "Readers also borrowed" on the book page (checklist step 339). The ranking looks at the whole
 * loan history and changes slowly, so only the list of book ids is cached (6 hours); the book
 * cards, with what's on the shelf now, are always fresh.
 */
@Service
public class AlsoBorrowedService {

  /** A suggestion needs this many different readers who borrowed both books (privacy). */
  static final int MIN_READERS = 2;

  static final int LIMIT = 8;

  private static final Duration TTL = Duration.ofHours(6);

  private final LoanLookupService loans;
  private final SearchService search;
  private final PublicCache cache;

  public AlsoBorrowedService(LoanLookupService loans, SearchService search, PublicCache cache) {
    this.loans = loans;
    this.search = search;
    this.cache = cache;
  }

  public List<BookListItem> forBook(UUID bookId) {
    List<UUID> ids =
        cache.get(
            "public:also-borrowed:" + bookId,
            TTL,
            new TypeReference<List<UUID>>() {},
            () -> loans.alsoBorrowed(bookId, MIN_READERS, LIMIT));
    return search.byIds(ids);
  }
}
