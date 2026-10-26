package lk.bookhive.catalog.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.bookhive.catalog.domain.Author;
import lk.bookhive.catalog.domain.AuthorRole;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.domain.Book.Contributor;
import lk.bookhive.catalog.domain.BookFormat;
import lk.bookhive.catalog.domain.Category;
import lk.bookhive.catalog.domain.Isbn;
import lk.bookhive.catalog.domain.Publisher;
import lk.bookhive.catalog.repository.AuthorRepository;
import lk.bookhive.catalog.repository.BookRepository;
import lk.bookhive.catalog.repository.CategoryRepository;
import lk.bookhive.catalog.repository.PublisherRepository;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.cache.PublicCache;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating, editing and reading titles (CAT-1, checklist step 157). */
@Service
public class BookService {

  private final BookRepository books;
  private final AuthorRepository authors;
  private final PublisherRepository publishers;
  private final CategoryRepository categories;
  private final AuditService audit;
  private final PublicCache cache;

  public BookService(
      BookRepository books,
      AuthorRepository authors,
      PublisherRepository publishers,
      CategoryRepository categories,
      AuditService audit,
      PublicCache cache) {
    this.books = books;
    this.authors = authors;
    this.publishers = publishers;
    this.categories = categories;
    this.audit = audit;
    this.cache = cache;
  }

  /** One contributor as sent by the form, in cover order. */
  public record ContributorForm(UUID authorId, AuthorRole role) {}

  /** What the cataloguer sends. {@code isbn} may be ISBN-10 or -13, with or without hyphens. */
  public record BookForm(
      String title,
      String subtitle,
      String isbn,
      String edition,
      Short publishYear,
      Integer pages,
      String language,
      String description,
      BookFormat format,
      String callNumber,
      UUID publisherId,
      List<ContributorForm> contributors,
      Set<UUID> categoryIds) {}

  /** The book page: the title with authors, publisher and categories, in one query. */
  @Transactional(readOnly = true)
  public Book get(UUID id) {
    return books
        .findWithDetailsById(id)
        .orElseThrow(() -> new NotFoundException("BOOK_NOT_FOUND", "Book not found."));
  }

  @Transactional
  public Book create(BookForm form, UUID staffId) {
    Isbn isbn = parseIsbn(form.isbn());
    checkIsbnFree(isbn, null);
    Book book = new Book(details(form, isbn));
    applyLinks(book, form);
    books.save(book);
    audit.record("book.created", staffId, "book", book.getId(), summary(book));
    return book;
  }

  @Transactional
  public Book update(UUID id, BookForm form, UUID staffId) {
    Book book = get(id);
    Isbn isbn = parseIsbn(form.isbn());
    checkIsbnFree(isbn, id);
    book.update(details(form, isbn));
    applyLinks(book, form);
    cache.evict(CatalogCacheKeys.book(id));
    audit.record("book.updated", staffId, "book", id, summary(book));
    return book;
  }

  /** Null for "no ISBN" (allowed: many older local books have none). */
  private static Isbn parseIsbn(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    return Isbn.parse(text)
        .orElseThrow(
            () ->
                new FieldValidationException(
                    "isbn",
                    "ISBN_INVALID",
                    "This isn't a valid ISBN. Check the digits (the last one is a check digit)."));
  }

  /** One title per ISBN (CAT-1): a clear 409 instead of a duplicate or a database error. */
  private void checkIsbnFree(Isbn isbn, UUID exceptId) {
    if (isbn == null) {
      return;
    }
    Optional<Book> existing = books.findByIsbn13(isbn.isbn13());
    if (existing.isPresent() && !existing.get().getId().equals(exceptId)) {
      throw new ConflictException(
          "BOOK_ISBN_EXISTS",
          "\"" + existing.get().getTitle() + "\" already has this ISBN. Add a copy to it instead.");
    }
  }

  private Book.Details details(BookForm form, Isbn isbn) {
    Publisher publisher =
        form.publisherId() == null
            ? null
            : publishers
                .findById(form.publisherId())
                .orElseThrow(
                    () ->
                        new FieldValidationException(
                            "publisherId", "PUBLISHER_NOT_FOUND", "Publisher not found."));
    return new Book.Details(
        form.title(),
        form.subtitle(),
        isbn == null ? null : isbn.isbn10(),
        isbn == null ? null : isbn.isbn13(),
        form.edition(),
        form.publishYear(),
        form.pages(),
        form.language().strip().toLowerCase(),
        form.description(),
        form.format() == null ? BookFormat.BOOK : form.format(),
        form.callNumber(),
        publisher);
  }

  /** Authors (in the order given) and categories; every id must exist. */
  private void applyLinks(Book book, BookForm form) {
    List<ContributorForm> wanted = form.contributors() == null ? List.of() : form.contributors();
    Map<UUID, Author> found =
        authors.findAllById(wanted.stream().map(ContributorForm::authorId).toList()).stream()
            .collect(Collectors.toMap(Author::getId, Function.identity()));
    book.setContributors(
        wanted.stream()
            .map(
                c -> {
                  Author author = found.get(c.authorId());
                  if (author == null) {
                    throw new FieldValidationException(
                        "contributors", "AUTHOR_NOT_FOUND", "An author in the list doesn't exist.");
                  }
                  return new Contributor(author, c.role() == null ? AuthorRole.AUTHOR : c.role());
                })
            .toList());

    Set<UUID> categoryIds = form.categoryIds() == null ? Set.of() : form.categoryIds();
    List<Category> chosen = categories.findAllById(categoryIds);
    if (chosen.size() != categoryIds.size()) {
      throw new FieldValidationException(
          "categoryIds", "CATEGORY_NOT_FOUND", "A chosen category doesn't exist.");
    }
    book.setCategories(new HashSet<>(chosen));
  }

  private static Map<String, Object> summary(Book book) {
    Map<String, Object> details = new HashMap<>();
    details.put("title", book.getTitle());
    if (book.getIsbn13() != null) {
      details.put("isbn13", book.getIsbn13());
    }
    return details;
  }
}
