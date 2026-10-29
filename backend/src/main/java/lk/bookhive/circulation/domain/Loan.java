package lk.bookhive.circulation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.exception.ConflictException;

/**
 * One loan of one copy to one member (table {@code loans}, checklist step 218). The copy and the
 * member are referenced by id: they belong to the catalog and member modules.
 *
 * <p>State changes go through methods named after what happens; the rules deciding whether they are
 * allowed (limits, holds, fines) are in the circulation services.
 */
@Entity
@Table(name = "loans")
public class Loan extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID copyId;

  // Null after anonymising a returned loan (reading history switched off, PRD §4.5)
  private UUID memberId;

  @Column(nullable = false, updatable = false)
  private Instant checkedOutAt;

  @Column(nullable = false)
  private LocalDate dueDate;

  @Column(nullable = false)
  private short renewCount;

  private Instant lastRenewedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private LoanStatus status;

  private Instant returnedAt;

  @Column(updatable = false)
  private UUID checkedOutBy;

  private UUID returnedBy;

  protected Loan() {
    // for JPA
  }

  /**
   * A new loan.
   *
   * @param dueDate already moved off closed days (DueDateCalculator)
   * @param staffId who checked it out at the desk
   */
  public static Loan checkOut(
      UUID copyId, UUID memberId, LocalDate dueDate, UUID staffId, Instant now) {
    Loan loan = new Loan();
    loan.copyId = copyId;
    loan.memberId = memberId;
    loan.checkedOutAt = now;
    loan.dueDate = dueDate;
    loan.status = LoanStatus.ACTIVE;
    loan.checkedOutBy = staffId;
    return loan;
  }

  /** Another loan period from today (PRD §4.2: not from the old due date). */
  public void renew(LocalDate newDueDate, Instant now) {
    requireOpen();
    this.dueDate = newDueDate;
    this.renewCount++;
    this.lastRenewedAt = now;
    this.status = LoanStatus.ACTIVE;
  }

  /**
   * The library is now closed on the due date (a new holiday, shorter weekly hours: step 357): the
   * book is due on the next open day instead. Not a renewal.
   */
  public void moveDueDate(LocalDate nextOpenDay) {
    requireOpen();
    this.dueDate = nextOpenDay;
  }

  /** The due date has passed (nightly job, checklist step 233). */
  public void markOverdue() {
    if (status == LoanStatus.ACTIVE) {
      this.status = LoanStatus.OVERDUE;
    }
  }

  /** Back at the library. Also used when a lost copy turns up again (step 230). */
  public void returnBack(UUID staffId, Instant now) {
    if (status != LoanStatus.ACTIVE && status != LoanStatus.OVERDUE && status != LoanStatus.LOST) {
      throw new ConflictException("LOAN_ALREADY_RETURNED", "This loan has already been returned.");
    }
    this.status = LoanStatus.RETURNED;
    this.returnedAt = now;
    this.returnedBy = staffId;
  }

  /** The member says it's lost (charged in step 229). */
  public void markLost() {
    requireOpen();
    this.status = LoanStatus.LOST;
  }

  /** Forget who borrowed it (only once returned). */
  public void anonymise() {
    if (status != LoanStatus.RETURNED) {
      throw new IllegalStateException("Only returned loans can be anonymised");
    }
    this.memberId = null;
  }

  /** Overdue on this library date (whether or not the nightly job has run yet). */
  public boolean isOverdueOn(LocalDate today) {
    return status.isOpen() && dueDate.isBefore(today);
  }

  private void requireOpen() {
    if (!status.isOpen()) {
      throw new ConflictException("LOAN_NOT_OPEN", "This loan is no longer open.");
    }
  }

  public UUID getCopyId() {
    return copyId;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public Instant getCheckedOutAt() {
    return checkedOutAt;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public int getRenewCount() {
    return renewCount;
  }

  public Instant getLastRenewedAt() {
    return lastRenewedAt;
  }

  public LoanStatus getStatus() {
    return status;
  }

  public Instant getReturnedAt() {
    return returnedAt;
  }

  public UUID getCheckedOutBy() {
    return checkedOutBy;
  }

  public UUID getReturnedBy() {
    return returnedBy;
  }
}
