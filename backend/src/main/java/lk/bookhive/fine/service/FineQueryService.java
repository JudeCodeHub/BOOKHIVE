package lk.bookhive.fine.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.circulation.service.BorrowingPorts.OutstandingBalance;
import lk.bookhive.common.domain.Money;
import lk.bookhive.fine.domain.Fine;
import lk.bookhive.fine.domain.FineStatus;
import lk.bookhive.fine.repository.FineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a member owes (step 274), and circulation's borrowing block (FIN-4, step 275): this is the
 * real {@link OutstandingBalance}, replacing the "nothing owed" default, so the desk refuses (or an
 * admin overrides) loans once the balance reaches the limit in the settings.
 */
@Service
public class FineQueryService implements OutstandingBalance {

  /**
   * @param balance what's owed now across open fines
   * @param blockAt borrowing stops from this balance (settings)
   * @param fines open fines first (oldest first), then the closed ones if asked for
   */
  public record MemberFines(Money balance, Money blockAt, List<Fine> fines) {

    public boolean blocked() {
      return balance.isAtLeast(blockAt);
    }
  }

  private final FineRepository fines;
  private final SettingsService settings;

  /**
   * Money on the staff dashboard (step 360), in LKR.
   *
   * @param collected payments minus refunds between start and end
   * @param waived waived between start and end
   * @param outstanding still owed on all open fines
   */
  public record MoneyTotals(BigDecimal collected, BigDecimal waived, BigDecimal outstanding) {}

  @Transactional(readOnly = true)
  public MoneyTotals moneyTotals(Instant start, Instant end) {
    FineRepository.MoneyTotals t = fines.moneyTotals(start, end);
    return new MoneyTotals(t.getCollected(), t.getWaived(), t.getOutstanding());
  }

  public FineQueryService(FineRepository fines, SettingsService settings) {
    this.fines = fines;
    this.settings = settings;
  }

  /** The member's balance: open fines' ledgers added up (FIN-4). */
  @Override
  @Transactional(readOnly = true)
  public Money of(UUID memberId) {
    return new Money(fines.outstandingFor(memberId));
  }

  /**
   * @param withClosed also settled and cancelled fines (history)
   */
  @Transactional(readOnly = true)
  public MemberFines forMember(UUID memberId, boolean withClosed) {
    List<FineStatus> statuses =
        withClosed
            ? List.of(FineStatus.OPEN, FineStatus.SETTLED, FineStatus.CANCELLED)
            : List.of(FineStatus.OPEN);
    List<Fine> list = fines.findByMemberIdAndStatusInOrderByCreatedAt(memberId, statuses);
    // Ledger lines are read inside the transaction (lazy collection)
    list.forEach(fine -> fine.getTransactions().size());
    List<Fine> ordered =
        list.stream()
            .sorted(
                (a, b) ->
                    Boolean.compare(
                        b.getStatus() == FineStatus.OPEN, a.getStatus() == FineStatus.OPEN))
            .toList();
    return new MemberFines(of(memberId), new Money(settings.fines().blockBorrowingAt()), ordered);
  }
}
