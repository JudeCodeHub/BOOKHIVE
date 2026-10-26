package lk.bookhive.circulation.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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
   * @param historyEnabled false when the member switched reading history off: nothing is listed
   */
  public record ReadingHistory(boolean historyEnabled, Page<LoanView> loans) {}

  private final LoanRepository loans;
  private final LendingCatalogService catalog;
  private final BorrowerService borrowers;
  private final LibraryTime time;

  public LoanHistoryService(
      LoanRepository loans,
      LendingCatalogService catalog,
      BorrowerService borrowers,
      LibraryTime time) {
    this.loans = loans;
    this.catalog = catalog;
    this.borrowers = borrowers;
    this.time = time;
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
    if (!borrower.readingHistoryEnabled()) {
      // Loans returned recently may not be anonymised yet (step 399): don't show them either
      return new ReadingHistory(false, Page.empty(request));
    }
    Page<Loan> returned =
        loans.findByMemberIdAndStatusOrderByCheckedOutAtDesc(
            memberId, LoanStatus.RETURNED, request);
    return new ReadingHistory(
        true,
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
    var today = time.today();
    return list.stream()
        .map(loan -> new LoanView(loan, copies.get(loan.getCopyId()), loan.isOverdueOn(today)))
        .toList();
  }
}
