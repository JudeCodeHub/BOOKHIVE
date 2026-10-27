package lk.bookhive.fine.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.circulation.service.FineCalculator;
import lk.bookhive.common.domain.Money;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.fine.domain.Fine;
import lk.bookhive.fine.domain.FineStatus;
import lk.bookhive.fine.domain.FineType;
import lk.bookhive.fine.repository.FineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts charges on the ledger (FIN-1, FIN-2, steps 271-273). Called inside the transaction of
 * whatever caused them (a return, a lost book, the nightly job), so the charge and its cause are
 * saved together or not at all.
 */
@Service
public class FineChargeService {

  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

  private final FineRepository fines;
  private final SettingsService settings;
  private final LibraryTime time;

  public FineChargeService(FineRepository fines, SettingsService settings, LibraryTime time) {
    this.fines = fines;
    this.settings = settings;
    this.time = time;
  }

  /**
   * Brings a loan's overdue fine up to what it should be on {@code asOf} (daily accrual, step 271):
   * the first chargeable day opens the fine, later days add one ledger line each. Adds nothing
   * within the grace days, after the cap, or when run twice in a day.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void accrueOverdue(
      UUID loanId, UUID memberId, LocalDate dueDate, String title, LocalDate asOf) {
    Fine fine = fines.findFirstByLoanIdAndType(loanId, FineType.OVERDUE).orElse(null);
    if (fine != null && (!fine.isAccruing() || fine.getStatus() == FineStatus.CANCELLED)) {
      // Already finalised (or withdrawn): nothing grows any more
      return;
    }
    Money charged = fine == null ? Money.ZERO : fine.charged();
    Money add = FineCalculator.stillToCharge(charged, dueDate, asOf, settings.fines());
    if (add.isZero()) {
      return;
    }
    String reason = "Overdue up to " + DAY.format(asOf);
    if (fine == null) {
      fines.save(
          Fine.open(
              memberId,
              loanId,
              FineType.OVERDUE,
              "Late return: " + title,
              true,
              add,
              reason,
              null,
              time.now()));
    } else {
      fine.charge(add, reason, null, time.now());
    }
  }

  /**
   * The book came back or was declared lost: the overdue fine becomes exactly {@code finalAmount}
   * (the calculator's figure for that day) and stops growing (step 272). Opens it if the loan was
   * late but the nightly job hadn't charged anything yet.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void finaliseOverdue(
      UUID loanId, UUID memberId, String title, Money finalAmount, String reason, UUID actorId) {
    Instant now = time.now();
    Fine fine = fines.findFirstByLoanIdAndType(loanId, FineType.OVERDUE).orElse(null);
    if (fine == null) {
      if (!finalAmount.isZero()) {
        fines.save(
            Fine.open(
                memberId,
                loanId,
                FineType.OVERDUE,
                "Late return: " + title,
                false,
                finalAmount,
                reason,
                actorId,
                now));
      }
      return;
    }
    if (fine.getStatus() == FineStatus.CANCELLED) {
      return;
    }
    Money missing = finalAmount.minus(fine.charged());
    if (!missing.isNegative() && !missing.isZero()) {
      fine.charge(missing, reason, actorId, now);
    }
    fine.stopAccruing(now);
  }

  /** A lost book: replacement cost plus processing fee, as one fine with two lines (step 273). */
  @Transactional(propagation = Propagation.MANDATORY)
  public Fine chargeLost(
      UUID loanId, UUID memberId, String title, Money replacement, Money fee, UUID actorId) {
    Instant now = time.now();
    Fine fine =
        Fine.open(
            memberId,
            loanId,
            FineType.LOST,
            "Lost book: " + title,
            false,
            replacement,
            "Replacement cost",
            actorId,
            now);
    if (!fee.isZero()) {
      fine.charge(fee, "Processing fee", actorId, now);
    }
    return fines.save(fine);
  }

  /** The "lost" book turned up: its lost charges are withdrawn (step 273, from step 230). */
  @Transactional(propagation = Propagation.MANDATORY)
  public void cancelLost(UUID loanId, UUID actorId) {
    fines
        .findFirstByLoanIdAndType(loanId, FineType.LOST)
        .filter(fine -> fine.getStatus() != FineStatus.CANCELLED)
        // A negative balance afterwards = it was paid: staff refund it (fines page, step 295)
        .ifPresent(
            fine -> fine.cancel("The lost book was found and returned", actorId, time.now()));
  }

  /** Damage set by staff at most the replacement cost, already checked (step 273). */
  @Transactional(propagation = Propagation.MANDATORY)
  public Fine chargeDamage(
      UUID loanId, UUID memberId, String title, Money amount, String note, UUID actorId) {
    return fines.save(
        Fine.open(
            memberId,
            loanId,
            FineType.DAMAGE,
            "Damaged book: " + title,
            false,
            amount,
            note,
            actorId,
            time.now()));
  }

  /** A paid membership type, for the year until {@code validUntil} (step 273). */
  @Transactional(propagation = Propagation.MANDATORY)
  public Fine chargeMembershipFee(UUID memberId, Money amount, LocalDate validUntil) {
    return fines.save(
        Fine.open(
            memberId,
            null,
            FineType.MEMBERSHIP_FEE,
            "Membership fee until " + DAY.format(validUntil),
            false,
            amount,
            "Annual membership fee",
            null,
            time.now()));
  }
}
