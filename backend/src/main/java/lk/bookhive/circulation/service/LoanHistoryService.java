package lk.bookhive.circulation.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.catalog.service.LendingCatalogService.LendableCopy;
import lk.bookhive.circulation.domain.Loan;
import lk.bookhive.circulation.domain.LoanStatus;
import lk.bookhive.circulation.repository.LoanRepository;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.member.service.BorrowerService.Borrower;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who borrowed what (checklist step 231), with the privacy rules of PRD §4.5:
 *
 * <ul>
 *   <li>A member sees their current loans, and their reading history (returned loans) unless they
 *       switched it off.
 *   <li>Staff see only what's needed to run the desk: a member's <b>current</b> loans, never their
 *       reading history; a copy's past loans with dates, but not who had it once it's back.
 * </ul>
 */
@Service
public class LoanHistoryService {

  /**
   * A loan with its copy.
   *
   * @param copy null if the copy was deleted from the catalog (not normally possible)
   * @param overdue past its due date today, even if the nightly job hasn't marked it yet
   */
  public record LoanView(Loan loan, LendableCopy copy, boolean overdue) {}

  /**
   * One past or current loan of a copy, for staff.
   *
   * @param memberNo who has it now; null once it's returned (privacy)
   */
  public record CopyLoan(Loan loan, String memberNo) {}

  /**
   * An open loan for the staff lists, with who has it and how to reach them.
   *
   * @param daysOverdue 0 when not overdue
   */
  public record OpenLoan(
      LoanView view, Borrower borrower, String fullName, String phone, long daysOverdue) {}

  /**
   * @param historyEnabled false when the member switched reading history off: nothing is listed
   * @param anonymiseAfterDays when it's off, returned loans lose the member's name after this
   */
  public record ReadingHistory(
      boolean historyEnabled, int anonymiseAfterDays, Page<LoanView> loans) {}

  private final LoanRepository loans;
  private final LendingCatalogService catalog;
  private final BorrowerService borrowers;
  private final AuthService authService;
  private final SettingsService settings;
  private final LibraryTime time;

  public LoanHistoryService(
      LoanRepository loans,
      LendingCatalogService catalog,
      BorrowerService borrowers,
      AuthService authService,
      SettingsService settings,
      LibraryTime time) {
    this.loans = loans;
    this.catalog = catalog;
    this.borrowers = borrowers;
    this.authService = authService;
    this.settings = settings;
    this.time = time;
  }

  /**
   * Everything out now, soonest due first; only the overdue loans, longest overdue first; or only
   * those due in the next few days (steps 232, 243). Current loans are operational data, so staff
   * see the borrower (PRD §4.5).
   *
   * @param dueWithinDays when set (and not overdueOnly): due from today to today + this many days
   */
  @Transactional(readOnly = true)
  public Page<OpenLoan> openLoans(boolean overdueOnly, Integer dueWithinDays, int page, int size) {
    LocalDate today = time.today();
    PageRequest request = PageRequest.of(page, size);
    Page<Loan> found;
    if (overdueOnly) {
      found =
          loans.findByStatusInAndDueDateBeforeOrderByDueDate(LoanRepository.OPEN, today, request);
    } else if (dueWithinDays != null) {
      found =
          loans.findByStatusInAndDueDateBetweenOrderByDueDate(
              LoanRepository.OPEN, today, today.plusDays(dueWithinDays), request);
    } else {
      found = loans.findByStatusInOrderByDueDate(LoanRepository.OPEN, request);
    }
    List<LoanView> views = withCopies(found.getContent());
    // One lookup per member on the page, not per loan
    Map<UUID, Borrower> members = new HashMap<>();
    Map<UUID, User> users = new HashMap<>();
    List<OpenLoan> rows =
        views.stream()
            .map(
                view -> {
                  Borrower borrower =
                      members.computeIfAbsent(
                          view.loan().getMemberId(), id -> borrowers.byId(id).orElse(null));
                  User user =
                      borrower == null
                          ? null
                          : users.computeIfAbsent(borrower.userId(), authService::getProfile);
                  long late =
                      view.overdue() ? ChronoUnit.DAYS.between(view.loan().getDueDate(), today) : 0;
                  return new OpenLoan(
                      view,
                      borrower,
                      user == null ? null : user.getFullName(),
                      user == null ? null : user.getPhone(),
                      late);
                })
            .toList();
    return new PageImpl<>(rows, request, found.getTotalElements());
  }

  /** What the member has out (or lost), soonest due first. For the member and for staff. */
  @Transactional(readOnly = true)
  public List<LoanView> current(UUID memberId) {
    return withCopies(
        loans.findByMemberIdAndStatusInOrderByDueDate(memberId, LoanRepository.CURRENT));
  }

  /** The member's own returned loans, newest first; empty when history is switched off. */
  @Transactional(readOnly = true)
  public ReadingHistory readingHistory(UUID memberId, int page, int size) {
    Borrower borrower =
        borrowers
            .byId(memberId)
            .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found."));
    PageRequest request = PageRequest.of(page, size);
    int anonymiseAfterDays = settings.membership().historyAnonymiseDays();
    if (!borrower.readingHistoryEnabled()) {
      // Loans returned recently may not be anonymised yet (step 399): don't show them either
      return new ReadingHistory(false, anonymiseAfterDays, Page.empty(request));
    }
    Page<Loan> returned =
        loans.findByMemberIdAndStatusOrderByCheckedOutAtDesc(
            memberId, LoanStatus.RETURNED, request);
    return new ReadingHistory(
        true,
        anonymiseAfterDays,
        new PageImpl<>(withCopies(returned.getContent()), request, returned.getTotalElements()));
  }

  /** A copy's loans, newest first, for staff: the borrower is shown only while it's out. */
  @Transactional(readOnly = true)
  public Page<CopyLoan> forCopy(UUID copyId, int page, int size) {
    if (catalog.byId(copyId).isEmpty()) {
      throw new NotFoundException("COPY_NOT_FOUND", "Copy not found.");
    }
    return loans
        .findByCopyIdOrderByCheckedOutAtDesc(copyId, PageRequest.of(page, size))
        .map(
            loan ->
                new CopyLoan(
                    loan,
                    LoanRepository.CURRENT.contains(loan.getStatus()) && loan.getMemberId() != null
                        ? borrowers.byId(loan.getMemberId()).map(Borrower::memberNo).orElse(null)
                        : null));
  }

  private List<LoanView> withCopies(List<Loan> list) {
    Map<UUID, LendableCopy> copies = catalog.byIds(list.stream().map(Loan::getCopyId).toList());
    LocalDate today = time.today();
    return list.stream()
        .map(loan -> new LoanView(loan, copies.get(loan.getCopyId()), loan.isOverdueOn(today)))
        .toList();
  }
}
