package lk.bookhive.catalog.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.catalog.domain.Author;
import lk.bookhive.catalog.repository.AuthorRepository;
import lk.bookhive.catalog.repository.BookAuthorRepository;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authors for cataloguing (CAT-6, checklist step 154). */
@Service
public class AuthorService {

  private static final int SUGGEST_LIMIT = 10;

  private final AuthorRepository authors;
  private final BookAuthorRepository bookAuthors;
  private final AuditService audit;

  public AuthorService(
      AuthorRepository authors, BookAuthorRepository bookAuthors, AuditService audit) {
    this.authors = authors;
    this.bookAuthors = bookAuthors;
    this.audit = audit;
  }

  /** Paged list A–Z; with {@code query}, only names containing it. */
  @Transactional(readOnly = true)
  public Page<Author> list(String query, int page, int size) {
    PageRequest request = PageRequest.of(page, size, Sort.by("name"));
    return query == null || query.isBlank()
        ? authors.findAll(request)
        : authors.findByNameContainingIgnoreCase(query.strip(), request);
  }

  /** For the author picker: best matches first, small typos allowed ("wickramasinge"). */
  @Transactional(readOnly = true)
  public List<Author> suggest(String text) {
    return text == null || text.isBlank()
        ? List.of()
        : authors.suggest(text.strip(), SUGGEST_LIMIT);
  }

  @Transactional(readOnly = true)
  public Author get(UUID id) {
    return require(id);
  }

  /** How many books an author is on (shown before deleting, and on the author page). */
  @Transactional(readOnly = true)
  public long bookCount(UUID id) {
    return bookAuthors.countByAuthorId(id);
  }

  @Transactional
  public Author create(String name, String bio, UUID staffId) {
    Author author = authors.save(new Author(name, bio));
    audit.record(
        "author.created", staffId, "author", author.getId(), Map.of("name", author.getName()));
    return author;
  }

  @Transactional
  public Author update(UUID id, String name, String bio, UUID staffId) {
    Author author = require(id);
    author.rename(name, bio);
    audit.record("author.updated", staffId, "author", id, Map.of("name", author.getName()));
    return author;
  }

  /** Only authors on no book: removing one from books would silently change the catalog. */
  @Transactional
  public void delete(UUID id, UUID staffId) {
    Author author = require(id);
    if (bookAuthors.existsByAuthorId(id)) {
      throw new ConflictException(
          "AUTHOR_HAS_BOOKS", "This author is on books in the catalog, so it can't be deleted.");
    }
    authors.delete(author);
    audit.record("author.deleted", staffId, "author", id, Map.of("name", author.getName()));
  }

  private Author require(UUID id) {
    return authors
        .findById(id)
        .orElseThrow(() -> new NotFoundException("AUTHOR_NOT_FOUND", "Author not found."));
  }
}
