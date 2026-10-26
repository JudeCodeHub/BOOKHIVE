package lk.bookhive.circulation.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.catalog.service.LendingCatalogService.LendableCopy;
import lk.bookhive.circulation.domain.Loan;
import lk.bookhive.circulation.event.CheckoutReceiptRequested;
import lk.bookhive.circulation.event.LoanCreated;
import lk.bookhive.circulation.repository.LoanRepository;
import lk.bookhive.circulation.service.EligibilityChecker.Problem;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.member.service.BorrowerService.Borrower;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lending at the desk (checklist steps 221-222): one member, one or more copies, all or nothing.
 *
 * <ol>
 *   <li>Lock every copy row (SELECT ... FOR UPDATE), in id order so two desks never deadlock.
 *   <li>Check every rule again under the lock: the desk's preview may be a minute old.
 *   <li>Any problem: lend nothing, report every problem per item. Otherwise create the loans and
 *       mark the copies on loan, in the same transaction.
 * </ol>
 *
 * The unique index "one open loan per copy" (V21) is the last safety net behind the lock.
 *
 * <p>An admin may lend anyway with a written reason (step 228) when every problem is overridable
 * (e.g. over the limit, unpaid fines); never when the copy is out or the membership isn't active.
 */
@Service
public class CheckoutService {

  /** Most items in one checkout (a family's weekly pile, not a whole shelf). */
  public static final int MAX_ITEMS = 20;

  private final BorrowerService borrowers;
  private final LendingCatalogService catalog;
  private final LoanRepository loans;
  private final EligibilityChecker eligibility;
  private final DueDateCalculator dueDates;
  private final AuthService authService;
  private final AuditService audit;
  private final ApplicationEventPublisher events;
  private final LibraryTime time;

  public CheckoutService(
      BorrowerService borrowers,
      LendingCatalogService catalog,
      LoanRepository loans,
      EligibilityChecker eligibility,
      DueDateCalculator dueDates,
      AuthService authService,
      AuditService audit,
      ApplicationEventPublisher events,
      LibraryTime time) {
    this.borrowers = borrowers;
    this.catalog = catalog;
    this.loans = loans;
    this.eligibility = eligibility;
    this.dueDates = dueDates;
    this.authService = authService;
    this.audit = audit;
    this.events = events;
    this.time = time;
  }

  /** The member at the desk: who they are and where they stand. */
  public record MemberSummary(
      Borrower borrower, String fullName, int itemsOut, List<Problem> problems) {}

  /**
   * One scanned item.
   *
   * @param copy null when the barcode is unknown
   * @param dueDate what it would be if lent today
   */
  public record ItemCheck(
      String barcode, LendableCopy copy, LocalDate dueDate, List<Problem> problems) {}

  /**
   * @param canOverride refused, but only for reasons an admin may override (step 228)
   */
  public record Preview(
      MemberSummary member, List<ItemCheck> items, boolean canCheckOut, boolean canOverride) {}

  /** A loan that was made, for the receipt. */
  public record Lent(Loan loan, LendableCopy copy) {}

  public record Receipt(MemberSummary member, List<Lent> loans) {}

  /** What would happen, without lending anything (the desk calls this while scanning, step 238). */
  @Transactional(readOnly = true)
  public Preview preview(String memberNo, List<String> barcodes) {
    Borrower borrower = requireBorrower(memberNo);
    List<String> scanned = normalise(barcodes);
    int itemsOut = itemsOut(borrower);
    LocalDate today = time.today();
    MemberSummary member = summary(borrower, itemsOut, today);

    List<ItemCheck> items = new ArrayList<>();
    for (int i = 0; i < scanned.size(); i++) {
      String barcode = scanned.get(i);
      Optional<LendableCopy> copy = catalog.byBarcode(barcode);
      items.add(itemCheck(borrower, barcode, copy.orElse(null), today, itemsOut + i));
    }
    return preview(member, items);
  }

  /**
   * Lends the copies, or nothing.
   *
   * @param overrideReason admins only: lend despite overridable problems, for this reason (audited)
   * @param emailReceipt also email the receipt to the member (after the checkout commits)
   * @throws CheckoutRefusedException with every problem per item, when anything isn't allowed
   */
  @PreAuthorize("#overrideReason == null or hasRole('ADMIN')")
  @Transactional
  public Receipt checkOut(
      String memberNo,
      List<String> barcodes,
      UUID staffId,
      String overrideReason,
      boolean emailReceipt) {
    Borrower borrower = requireBorrower(memberNo);
    List<String> scanned = normalise(barcodes);
    if (scanned.isEmpty()) {
      throw new FieldValidationException("barcodes", "NO_ITEMS", "Scan at least one item.");
    }
    LocalDate today = time.today();
    Instant now = time.now();

    // Ids only (nothing loaded yet), then lock in id order: the same order on every desk, so
    // two checkouts never deadlock, and every copy is read fresh under its lock
    Map<String, UUID> ids = new HashMap<>();
    for (String barcode : scanned) {
      catalog.idByBarcode(barcode).ifPresent(id -> ids.put(barcode, id));
    }
    Map<UUID, LendableCopy> locked = new HashMap<>();
    ids.values().stream()
        .sorted(Comparator.naturalOrder())
        .forEach(id -> locked.put(id, catalog.lockForLending(id)));

    int itemsOut = itemsOut(borrower);
    MemberSummary member = summary(borrower, itemsOut, today);
    List<ItemCheck> items = new ArrayList<>();
    for (int i = 0; i < scanned.size(); i++) {
      String barcode = scanned.get(i);
      UUID id = ids.get(barcode);
      items.add(
          itemCheck(borrower, barcode, id == null ? null : locked.get(id), today, itemsOut + i));
    }
    Preview checked = preview(member, items);
    boolean overriding = !checked.canCheckOut() && overrideReason != null;
    if (!checked.canCheckOut() && !(overriding && checked.canOverride())) {
      throw new CheckoutRefusedException(checked);
    }

    List<Lent> lent = new ArrayList<>();
    for (ItemCheck item : items) {
      Loan loan =
          loans.save(
              Loan.checkOut(
                  item.copy().copyId(), borrower.memberId(), item.dueDate(), staffId, now));
      catalog.lend(item.copy().copyId(), staffId, now);
      lent.add(new Lent(loan, item.copy()));
      // Listeners use @TransactionalEventListener: they only hear it if the checkout commits
      events.publishEvent(
          new LoanCreated(
              loan.getId(),
              borrower.memberId(),
              item.copy().copyId(),
              item.copy().bookId(),
              item.copy().barcode(),
              item.copy().title(),
              now,
              loan.getDueDate(),
              staffId));
    }
    audit.record(
        "loan.checked_out",
        staffId,
        "member",
        borrower.memberId(),
        Map.of("copies", lent.stream().map(l -> l.copy().barcode()).toList()));
    if (overriding) {
      audit.record(
          "loan.override",
          staffId,
          "member",
          borrower.memberId(),
          Map.of(
              "action", "checkout",
              "reason", overrideReason.strip(),
              "overridden", overridden(member, items),
              "copies", lent.stream().map(l -> l.copy().barcode()).toList()));
    }
    if (emailReceipt) {
      User user = authService.getProfile(borrower.userId());
      events.publishEvent(
          new CheckoutReceiptRequested(
              user.getEmail(),
              user.getFullName(),
              user.getPreferredLanguage(),
              borrower.memberNo(),
              now,
              lent.stream()
                  .map(
                      l ->
                          new CheckoutReceiptRequested.Line(
                              l.copy().title(), l.copy().barcode(), l.loan().getDueDate()))
                  .toList()));
    }
    return new Receipt(summary(borrower, itemsOut + lent.size(), today), lent);
  }

  private ItemCheck itemCheck(
      Borrower borrower, String barcode, LendableCopy copy, LocalDate today, int itemsBefore) {
    if (copy == null) {
      return new ItemCheck(
          barcode,
          null,
          null,
          List.of(
              new Problem(
                  EligibilityChecker.Reason.COPY_NOT_AVAILABLE,
                  "No copy has the barcode " + barcode + ".")));
    }
    List<Problem> problems = new ArrayList<>(eligibility.copy(borrower, copy));
    // The limit counts what's already out plus the items scanned before this one
    if (itemsBefore >= borrower.maxItems()) {
      problems.add(
          new Problem(
              EligibilityChecker.Reason.LOAN_LIMIT_REACHED,
              "Over the limit of " + borrower.maxItems() + " items."));
    }
    return new ItemCheck(barcode, copy, dueDates.dueDate(today, borrower.loanDays()), problems);
  }

  private MemberSummary summary(Borrower borrower, int itemsOut, LocalDate today) {
    // The limit is shown per item; at member level only "is anything blocking at all"
    List<Problem> problems =
        eligibility.member(borrower, today, 0).stream()
            .filter(p -> p.reason() != EligibilityChecker.Reason.LOAN_LIMIT_REACHED)
            .toList();
    String name = authService.getProfile(borrower.userId()).getFullName();
    return new MemberSummary(borrower, name, itemsOut, problems);
  }

  private static Preview preview(MemberSummary member, List<ItemCheck> items) {
    List<Problem> all = allProblems(member, items);
    boolean ok = all.isEmpty() && !items.isEmpty();
    boolean overridable =
        !ok && !items.isEmpty() && all.stream().allMatch(p -> p.reason().overridable());
    return new Preview(member, items, ok, overridable);
  }

  private static List<Problem> allProblems(MemberSummary member, List<ItemCheck> items) {
    List<Problem> all = new ArrayList<>(member.problems());
    items.forEach(item -> all.addAll(item.problems()));
    return all;
  }

  /** For the audit log: which rules were set aside, e.g. [LOAN_LIMIT_REACHED]. */
  private static List<String> overridden(MemberSummary member, List<ItemCheck> items) {
    return allProblems(member, items).stream().map(p -> p.reason().name()).distinct().toList();
  }

  private int itemsOut(Borrower borrower) {
    return (int) loans.countByMemberIdAndStatusIn(borrower.memberId(), LoanRepository.OPEN);
  }

  private Borrower requireBorrower(String memberNo) {
    return borrowers
        .byMemberNo(memberNo)
        .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "No member has this number."));
  }

  /** Upper-case, no blanks, each barcode once (a double scan is not two copies). */
  private static List<String> normalise(List<String> barcodes) {
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String barcode : barcodes == null ? List.<String>of() : barcodes) {
      if (barcode != null && !barcode.isBlank()) {
        unique.add(barcode.strip().toUpperCase(Locale.ROOT));
      }
    }
    if (unique.size() > MAX_ITEMS) {
      throw new FieldValidationException(
          "barcodes", "TOO_MANY_ITEMS", "At most " + MAX_ITEMS + " items in one checkout.");
    }
    return List.copyOf(unique);
  }

  /** Nothing was lent; the body lists every problem per item, like the preview. */
  public static class CheckoutRefusedException extends BusinessRuleException {

    private final transient Preview details;

    CheckoutRefusedException(Preview details) {
      super("CHECKOUT_REFUSED", "Nothing was lent: some items can't be checked out.");
      this.details = details;
    }

    public Preview getDetails() {
      return details;
    }
  }
}
