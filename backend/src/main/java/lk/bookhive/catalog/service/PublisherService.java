package lk.bookhive.catalog.service;

import java.util.Map;
import java.util.UUID;
import lk.bookhive.catalog.domain.Publisher;
import lk.bookhive.catalog.repository.BookRepository;
import lk.bookhive.catalog.repository.PublisherRepository;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishers for cataloguing (CAT-6, checklist step 155). Names are unique, ignoring case. */
@Service
public class PublisherService {

  private final PublisherRepository publishers;
  private final BookRepository books;
  private final AuditService audit;

  public PublisherService(
      PublisherRepository publishers, BookRepository books, AuditService audit) {
    this.publishers = publishers;
    this.books = books;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public Page<Publisher> list(String query, int page, int size) {
    PageRequest request = PageRequest.of(page, size, Sort.by("name"));
    return query == null || query.isBlank()
        ? publishers.findAll(request)
        : publishers.findByNameContainingIgnoreCase(query.strip(), request);
  }

  @Transactional(readOnly = true)
  public Publisher get(UUID id) {
    return require(id);
  }

  @Transactional
  public Publisher create(String name, UUID staffId) {
    checkUnique(name, null);
    Publisher publisher = publishers.save(new Publisher(name));
    audit.record(
        "publisher.created", staffId, "publisher", publisher.getId(), Map.of("name", name.strip()));
    return publisher;
  }

  @Transactional
  public Publisher update(UUID id, String name, UUID staffId) {
    Publisher publisher = require(id);
    checkUnique(name, id);
    publisher.rename(name);
    audit.record("publisher.updated", staffId, "publisher", id, Map.of("name", name.strip()));
    return publisher;
  }

  @Transactional
  public void delete(UUID id, UUID staffId) {
    Publisher publisher = require(id);
    if (books.existsByPublisherId(id)) {
      throw new ConflictException(
          "PUBLISHER_HAS_BOOKS",
          "Books in the catalog have this publisher, so it can't be deleted.");
    }
    publishers.delete(publisher);
    audit.record(
        "publisher.deleted", staffId, "publisher", id, Map.of("name", publisher.getName()));
  }

  /** A clear 409 instead of a database error when the name is taken (any letter case). */
  private void checkUnique(String name, UUID exceptId) {
    publishers
        .findByName(name.strip())
        .filter(existing -> !existing.getId().equals(exceptId))
        .ifPresent(
            existing -> {
              throw new ConflictException(
                  "PUBLISHER_EXISTS", "A publisher with this name already exists.");
            });
  }

  private Publisher require(UUID id) {
    return publishers
        .findById(id)
        .orElseThrow(() -> new NotFoundException("PUBLISHER_NOT_FOUND", "Publisher not found."));
  }
}
