package lk.bookhive.circulation.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.catalog.service.LendingCatalogService.LendableCopy;
import lk.bookhive.circulation.domain.Loan;
import lk.bookhive.circulation.event.LoanRenewed;
import lk.bookhive.circulation.repository.LoanRepository;
import lk.bookhive.circulation.service.BorrowingPorts.HoldDemand;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.member.service.BorrowerService.Borrower;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renewing a loan (PRD §4.2, checklist step 226). Not allowed when the renewals of the member's
 * type are used up, someone has reserved the title, the member is blocked, or the loan is already
 * overdue (it has to come back to the desk; staff can override, step 228). The new due date is
 * counted from today, moved to an open day. Every refusal reason is reported at once.
 */
@Service
public class RenewalService {

  /** Why a renewal isn't allowed. */
  public enum RenewalReason {
    MAX_RENEWALS_REACHED,
    RESERVED_BY_ANOTHER_MEMBER,
    MEMBER_BLOCKED,
    LOAN_OVERDUE,
    NOT_NEEDED_YET
  }

  public record RenewalProblem(RenewalReason reason, String message) {}

  /** A loan with its copy, for the response. */
  public record Renewed(Loan loan, LendableCopy copy) {}

  private final LoanRepository loans;
  private final BorrowerService borrowers;
  private final LendingCatalogService catalog;
  private final EligibilityChecker eligibility;
  private final DueDateCalculator dueDates;
  private final HoldDemand holdDemand;
  private final ApplicationEventPublisher events;
  private final AuditService audit;
  private final LibraryTime time;

  public RenewalService(
      LoanRepository loans,
      BorrowerService borrowers,
      LendingCatalogService catalog,
      EligibilityChecker eligibility,
      DueDateCalculator dueDates,
      ObjectProvider<HoldDemand> holdDemand,
      ApplicationEventPublisher events,
      AuditService audit,
      LibraryTime time) {
    this.loans = loans;
    this.borrowers = borrowers;
    this.catalog = catalog;
    this.eligibility = eligibility;
    this.dueDates = dueDates;
    // Until the hold module exists, nobody has reserved anything
    this.holdDemand = holdDemand.getIfAvailable(() -> bookId -> false);
    this.events = events;
    this.audit = audit;
    this.time = time;
  }

  /** Why this loan can't be renewed right now; empty = it can. */
  @Transactional(readOnly = true)
  public List<RenewalProblem> check(UUID loanId) {
    Loan loan = require(loanId);
    return problems(loan, borrower(loan), copy(loan), time.today());
  }

  /**
   * Renews the loan.
   *
   * @param asMemberId when a member renews their own loan: refused if it isn't theirs
   * @throws RenewalRefusedException with every reason when it isn't allowed
   */
  @Transactional
  public Renewed renew(UUID loanId, UUID asMemberId, UUID actorId) {
    Loan loan = require(loanId);
    if (asMemberId != null && !asMemberId.equals(loan.getMemberId())) {
      // Not "forbidden": don't confirm that someone else's loan exists
      throw new NotFoundException("LOAN_NOT_FOUND", "Loan not found.");
    }
    Borrower borrower = borrower(loan);
    LendableCopy copy = copy(loan);
    LocalDate today = time.today();
    List<RenewalProblem> problems = problems(loan, borrower, copy, today);
    if (!problems.isEmpty()) {
      throw new RenewalRefusedException(problems);
    }

    LocalDate oldDue = loan.getDueDate();
    loan.renew(dueDates.dueDate(today, borrower.loanDays()), time.now());
    events.publishEvent(
        new LoanRenewed(
            loan.getId(),
            loan.getMemberId(),
            loan.getCopyId(),
            oldDue,
            loan.getDueDate(),
            loan.getRenewCount()));
    audit.record(
        "loan.renewed",
        actorId,
        "loan",
        loan.getId(),
        Map.of("from", oldDue.toString(), "to", loan.getDueDate().toString()));
    return new Renewed(loan, copy);
  }

  private List<RenewalProblem> problems(
      Loan loan, Borrower borrower, LendableCopy copy, LocalDate today) {
    List<RenewalProblem> problems = new ArrayList<>();
    if (!loan.getStatus().isOpen()) {
      throw new BusinessRuleException("LOAN_NOT_OPEN", "This loan is no longer open.");
    }
    if (loan.getRenewCount() >= borrower.maxRenewals()) {
      problems.add(
          new RenewalProblem(
              RenewalReason.MAX_RENEWALS_REACHED,
              "Already renewed "
                  + loan.getRenewCount()
                  + " of "
                  + borrower.maxRenewals()
                  + " times."));
    }
    if (holdDemand.isWanted(copy.bookId())) {
      problems.add(
          new RenewalProblem(
              RenewalReason.RESERVED_BY_ANOTHER_MEMBER,
              "Another member has reserved this book, so it has to come back."));
    }
    // Status, expiry and fines block renewing like borrowing (the loan limit doesn't apply)
    eligibility.member(borrower, today, 0).stream()
        .filter(p -> p.reason() != EligibilityChecker.Reason.LOAN_LIMIT_REACHED)
        .forEach(p -> problems.add(new RenewalProblem(RenewalReason.MEMBER_BLOCKED, p.message())));
    if (loan.isOverdueOn(today)) {
      problems.add(
          new RenewalProblem(
              RenewalReason.LOAN_OVERDUE, "This loan is overdue: please return it to the desk."));
    }
    // Renewing on the day of checkout would give the same due date: nothing to gain
    if (!dueDates.dueDate(today, borrower.loanDays()).isAfter(loan.getDueDate())) {
      problems.add(
          new RenewalProblem(
              RenewalReason.NOT_NEEDED_YET, "Renewing now wouldn't give you a later due date."));
    }
    return problems;
  }

  private Borrower borrower(Loan loan) {
    return borrowers
        .byId(loan.getMemberId())
        .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found."));
  }

  private LendableCopy copy(Loan loan) {
    return catalog
        .byId(loan.getCopyId())
        .orElseThrow(() -> new NotFoundException("COPY_NOT_FOUND", "Copy not found."));
  }

  private Loan require(UUID loanId) {
    return loans
        .findById(loanId)
        .orElseThrow(() -> new NotFoundException("LOAN_NOT_FOUND", "Loan not found."));
  }

  /** The renewal wasn't made; carries every reason. */
  public static class RenewalRefusedException extends BusinessRuleException {

    private final transient List<RenewalProblem> problems;

    RenewalRefusedException(List<RenewalProblem> problems) {
      super("RENEWAL_REFUSED", problems.getFirst().message());
      this.problems = List.copyOf(problems);
    }

    public List<RenewalProblem> getProblems() {
      return problems;
    }
  }
}
