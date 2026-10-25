package lk.bookhive.catalog.service;

import jakarta.persistence.criteria.Join;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.domain.BookAuthor;
import lk.bookhive.catalog.domain.BookFormat;
import lk.bookhive.catalog.domain.Category;
import lk.bookhive.catalog.repository.BookAuthorRepository;
import lk.bookhive.catalog.repository.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Browsing the catalog page by page (checklist step 158). Keyword search with ranking and typo
 * tolerance is the search phase; this is the plain list with filters and sorting.
 */
@Service
public class BookListService {

  private final BookRepository books;
  private final BookAuthorRepository bookAuthors;

  public BookListService(BookRepository books, BookAuthorRepository bookAuthors) {
    this.books = books;
    this.bookAuthors = bookAuthors;
  }

  /** How the list is ordered. */
  public enum BookSort {
    /** A–Z by title */
    TITLE(Sort.by("title")),
    /** Most recently added to the catalog first ("New arrivals") */
    NEWEST(Sort.by(Sort.Direction.DESC, "createdAt")),
    /** Most recently published first */
    YEAR(Sort.by(Sort.Order.desc("publishYear").nullsLast(), Sort.Order.asc("title")));

    private final Sort sort;

    BookSort(Sort sort) {
      this.sort = sort;
    }
  }

  /** Optional filters; null means "any". */
  public record Filters(String language, BookFormat format, UUID categoryId) {}

  /** A book in a list, with its contributors' names in cover order. */
  public record BookListItem(Book book, List<String> contributorNames) {}

  @Transactional(readOnly = true)
  public Page<BookListItem> list(Filters filters, BookSort sort, int page, int size) {
    Page<Book> found = books.findAll(specification(filters), PageRequest.of(page, size, sort.sort));

    // Second query: all contributors of this page at once, grouped per book
    List<UUID> ids = found.stream().map(Book::getId).toList();
    Map<UUID, List<String>> names =
        bookAuthors.findByBookIdInOrderByPosition(ids).stream()
            .collect(
                Collectors.groupingBy(
                    contributor -> contributor.getBook().getId(),
                    Collectors.mapping(
                        (BookAuthor contributor) -> contributor.getAuthor().getName(),
                        Collectors.toList())));
    return found.map(book -> new BookListItem(book, names.getOrDefault(book.getId(), List.of())));
  }

  private static Specification<Book> specification(Filters filters) {
    Specification<Book> spec = Specification.unrestricted();
    if (filters.language() != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("language"), filters.language()));
    }
    if (filters.format() != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("format"), filters.format()));
    }
    if (filters.categoryId() != null) {
      spec =
          spec.and(
              (root, query, cb) -> {
                Join<Book, Category> categories = root.join("categories");
                return cb.equal(categories.get("id"), filters.categoryId());
              });
    }
    return spec;
  }
}
