package lk.bookhive.hold.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.catalog.service.LendingCatalogService.HoldableTitle;
import lk.bookhive.circulation.service.EligibilityChecker;
import lk.bookhive.circulation.service.LoanLookupService;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.hold.domain.Hold;
import lk.bookhive.hold.repository.HoldRepository;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.member.service.BorrowerService.Borrower;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Placing a hold (PRD §4.3, HOLD-1, checklist step 251). Allowed only when:
 *
 * <ul>
 *   <li>the member may borrow at all (active, not expired, fines below the limit);
 *   <li>the title has copies that may go home, but <b>none is on the shelf</b> now (otherwise: just
 *       come and borrow it);
 *   <li>the member is under their type's max holds (3 by default);
 *   <li>they don't have the book out already, or a hold on it already;
 *   <li>the book is from a section their membership may borrow from (juniors).
 * </ul>
 *
 * Every reason is reported at once, like the checkout and renewal rules.
 */
@Service
public class HoldService {

  /** Why a hold can't be placed. */
  public enum HoldRefusal {
    MEMBER_NOT_ACTIVE,
    MEMBERSHIP_EXPIRED,
    FINES_BLOCK_BORROWING,
    NOT_LENDABLE,
    COPY_AVAILABLE,
    MAX_HOLDS_REACHED,
    ALREADY_BORROWED,
    ALREADY_HELD,
    SECTION_NOT_ALLOWED
  }

  public record HoldProblem(HoldRefusal reason, String message) {}

  private final HoldRepository holds;
  private final BorrowerService borrowers;
  private final LendingCatalogService catalog;
  private final LoanLookupService loans;
  private final EligibilityChecker eligibility;
  private final AuditService audit;
  private final LibraryTime time;

  public HoldService(
      HoldRepository holds,
      BorrowerService borrowers,
      LendingCatalogService catalog,
      LoanLookupService loans,
      EligibilityChecker eligibility,
      AuditService audit,
      LibraryTime time) {
    this.holds = holds;
    this.borrowers = borrowers;
    this.catalog = catalog;
    this.loans = loans;
    this.eligibility = eligibility;
    this.audit = audit;
    this.time = time;
  }

  /** Why this member can't place a hold on this book now; empty = they can. */
  @Transactional(readOnly = true)
  public List<HoldProblem> check(UUID memberId, UUID bookId) {
    return problems(requireBorrower(memberId), requireTitle(bookId), time.today());
  }

  /**
   * Joins the queue for the title.
   *
   * @param placedBy staff placing it for the member at the desk (step 258); null when the member
   *     places it themselves
   * @throws HoldRefusedException with every reason when it isn't allowed
   */
  @Transactional
  public Hold place(UUID memberId, UUID bookId, UUID placedBy) {
    Borrower borrower = requireBorrower(memberId);
    HoldableTitle title = requireTitle(bookId);
    List<HoldProblem> problems = problems(borrower, title, time.today());
    if (!problems.isEmpty()) {
      throw new HoldRefusedException(problems);
    }
    Instant now = time.now();
    Hold hold;
    try {
      hold = holds.saveAndFlush(Hold.place(bookId, memberId, placedBy, now));
    } catch (DataIntegrityViolationException e) {
      // Two clicks at the same moment: the unique index (one open hold per member per title) won
      throw new ConflictException("HOLD_ALREADY_PLACED", "You already have a hold on this book.");
    }
    audit.record(
        "hold.placed",
        placedBy != null ? placedBy : borrower.userId(),
        "hold",
        hold.getId(),
        Map.of("bookId", bookId.toString(), "memberNo", borrower.memberNo()));
    return hold;
  }

  private List<HoldProblem> problems(Borrower borrower, HoldableTitle title, LocalDate today) {
    List<HoldProblem> problems = new ArrayList<>();
    // Same member rules as borrowing (the loan limit doesn't matter for a hold)
    eligibility.member(borrower, today, 0).stream()
        .filter(p -> p.reason() != EligibilityChecker.Reason.LOAN_LIMIT_REACHED)
        .forEach(
            p ->
                problems.add(new HoldProblem(HoldRefusal.valueOf(p.reason().name()), p.message())));

    if (title.lendableCopies() == 0) {
      problems.add(
          new HoldProblem(
              HoldRefusal.NOT_LENDABLE,
              "This book has no copies that can be borrowed (reference only)."));
    } else if (title.availableToBorrow() > 0) {
      problems.add(
          new HoldProblem(
              HoldRefusal.COPY_AVAILABLE,
              "A copy is on the shelf now: come and borrow it, no hold needed."));
    }
    if (title.sections().stream().noneMatch(borrower::mayBorrowFrom)) {
      problems.add(
          new HoldProblem(
              HoldRefusal.SECTION_NOT_ALLOWED,
              "This membership can't borrow from " + String.join(", ", title.sections()) + "."));
    }

    if (holds
        .findFirstByMemberIdAndBookIdAndStatusIn(
            borrower.memberId(), title.bookId(), HoldRepository.OPEN)
        .isPresent()) {
      problems.add(
          new HoldProblem(HoldRefusal.ALREADY_HELD, "You already have a hold on this book."));
    } else {
      long open = holds.countByMemberIdAndStatusIn(borrower.memberId(), HoldRepository.OPEN);
      if (open >= borrower.maxHolds()) {
        problems.add(
            new HoldProblem(
                HoldRefusal.MAX_HOLDS_REACHED,
                "You have " + open + " of " + borrower.maxHolds() + " holds already."));
      }
    }
    if (loans.hasBookOut(borrower.memberId(), title.bookId())) {
      problems.add(
          new HoldProblem(HoldRefusal.ALREADY_BORROWED, "You have this book out already."));
    }
    return problems;
  }

  private Borrower requireBorrower(UUID memberId) {
    return borrowers
        .byId(memberId)
        .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found."));
  }

  private HoldableTitle requireTitle(UUID bookId) {
    return catalog
        .holdableTitle(bookId)
        .orElseThrow(() -> new NotFoundException("BOOK_NOT_FOUND", "Book not found."));
  }

  /** The hold wasn't placed; carries every reason. */
  public static class HoldRefusedException extends BusinessRuleException {

    private final transient List<HoldProblem> problems;

    HoldRefusedException(List<HoldProblem> problems) {
      super("HOLD_REFUSED", problems.getFirst().message());
      this.problems = List.copyOf(problems);
    }

    public List<HoldProblem> getProblems() {
      return problems;
    }
  }
}
