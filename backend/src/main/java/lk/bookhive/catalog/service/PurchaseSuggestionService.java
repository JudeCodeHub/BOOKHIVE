package lk.bookhive.catalog.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.catalog.domain.Book;
import lk.bookhive.catalog.domain.Isbn;
import lk.bookhive.catalog.domain.PurchaseSuggestion;
import lk.bookhive.catalog.domain.SuggestionStatus;
import lk.bookhive.catalog.repository.BookRepository;
import lk.bookhive.catalog.repository.PurchaseSuggestionRepository;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "Please buy this book" from members, answered by staff (CAT-11, checklist step 170). */
@Service
public class PurchaseSuggestionService {

  /** Open suggestions per member: enough to be useful, few enough to stay meaningful. */
  public static final int MAX_OPEN = 5;

  private static final Set<SuggestionStatus> OPEN =
      Set.of(SuggestionStatus.SUBMITTED, SuggestionStatus.ORDERED);

  private final PurchaseSuggestionRepository suggestions;
  private final BookRepository books;
  private final AuthService authService;
  private final AuditService audit;
  private final LibraryTime time;

  /** Suggestions nobody has looked at yet (staff dashboard, step 360). */
  @Transactional(readOnly = true)
  public long newCount() {
    return suggestions.countByStatus(SuggestionStatus.SUBMITTED);
  }

  public PurchaseSuggestionService(
      PurchaseSuggestionRepository suggestions,
      BookRepository books,
      AuthService authService,
      AuditService audit,
      LibraryTime time) {
    this.suggestions = suggestions;
    this.books = books;
    this.authService = authService;
    this.audit = audit;
    this.time = time;
  }

  /** A suggestion for the staff list: who asked, and how many members asked for the same ISBN. */
  public record StaffView(PurchaseSuggestion suggestion, User suggestedBy, long sameIsbnCount) {}

  @Transactional(readOnly = true)
  public List<PurchaseSuggestion> mine(UUID userId) {
    return suggestions.findByUserIdOrderByCreatedAtDesc(userId);
  }

  @Transactional
  public PurchaseSuggestion suggest(
      UUID userId, String title, String author, String isbnText, String reason) {
    if (suggestions.countByUserIdAndStatusIn(userId, OPEN) >= MAX_OPEN) {
      throw new BusinessRuleException(
          "SUGGESTION_LIMIT",
          "You have " + MAX_OPEN + " open suggestions. Please wait until staff answer them.");
    }
    String isbn13 = null;
    if (isbnText != null && !isbnText.isBlank()) {
      isbn13 =
          Isbn.parse(isbnText)
              .orElseThrow(
                  () ->
                      new FieldValidationException(
                          "isbn", "ISBN_INVALID", "This isn't a valid ISBN. Check the digits."))
              .isbn13();
      if (books.findByIsbn13(isbn13).isPresent()) {
        throw new ConflictException(
            "BOOK_ALREADY_IN_CATALOG", "The library already has this book. You can borrow it.");
      }
      if (suggestions.existsByUserIdAndIsbn13AndStatusIn(userId, isbn13, OPEN)) {
        throw new ConflictException("SUGGESTION_EXISTS", "You have already suggested this book.");
      }
    }
    PurchaseSuggestion suggestion =
        suggestions.save(new PurchaseSuggestion(userId, title, author, isbn13, reason));
    audit.record(
        "suggestion.created", userId, "suggestion", suggestion.getId(), Map.of("title", title));
    return suggestion;
  }

  /** Staff list by status, oldest first so nobody waits longest. */
  @Transactional(readOnly = true)
  public Page<StaffView> list(SuggestionStatus status, int page, int size) {
    Page<PurchaseSuggestion> found =
        suggestions.findByStatus(status, PageRequest.of(page, size, Sort.by("createdAt")));
    Set<UUID> userIds =
        found.stream().map(PurchaseSuggestion::getUserId).collect(Collectors.toSet());
    Map<UUID, User> users = authService.findUsers(userIds);
    return found.map(
        s ->
            new StaffView(
                s,
                users.get(s.getUserId()),
                s.getIsbn13() == null ? 1 : suggestions.countByIsbn13(s.getIsbn13())));
  }

  @Transactional
  public PurchaseSuggestion decide(
      UUID id, SuggestionStatus status, String note, UUID bookId, UUID staffId) {
    PurchaseSuggestion suggestion =
        suggestions
            .findById(id)
            .orElseThrow(
                () -> new NotFoundException("SUGGESTION_NOT_FOUND", "Suggestion not found."));
    Book book =
        bookId == null
            ? null
            : books
                .findById(bookId)
                .orElseThrow(
                    () ->
                        new FieldValidationException(
                            "bookId", "BOOK_NOT_FOUND", "Book not found."));
    suggestion.decide(status, note, book, staffId, time.now());
    audit.record("suggestion.decided", staffId, "suggestion", id, Map.of("status", status.name()));
    return suggestion;
  }
}
