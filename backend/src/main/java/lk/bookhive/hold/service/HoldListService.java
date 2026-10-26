package lk.bookhive.hold.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.catalog.service.LendingCatalogService;
import lk.bookhive.catalog.service.LendingCatalogService.HoldableTitle;
import lk.bookhive.catalog.service.LendingCatalogService.LendableCopy;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.hold.domain.Hold;
import lk.bookhive.hold.domain.HoldStatus;
import lk.bookhive.hold.repository.HoldRepository;
import lk.bookhive.hold.service.HoldQueueService.HoldView;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.member.service.BorrowerService.Borrower;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Holds with what screens show next to them: title, set-aside copy, member (steps 257, 262). */
@Service
public class HoldListService {

  /**
   * A member's hold.
   *
   * @param copy the copy set aside (READY only)
   */
  public record HoldRow(HoldView view, HoldableTitle title, LendableCopy copy) {}

  /**
   * One copy on the hold shelf, for staff.
   *
   * @param pastDeadline its last pickup day has passed (the nightly job expires it)
   */
  public record ShelfRow(
      Hold hold,
      LendableCopy copy,
      Borrower borrower,
      String fullName,
      String phone,
      boolean pastDeadline) {}

  private final HoldRepository holds;
  private final HoldQueueService queue;
  private final LendingCatalogService catalog;
  private final BorrowerService borrowers;
  private final AuthService authService;
  private final LibraryTime time;

  public HoldListService(
      HoldRepository holds,
      HoldQueueService queue,
      LendingCatalogService catalog,
      BorrowerService borrowers,
      AuthService authService,
      LibraryTime time) {
    this.holds = holds;
    this.queue = queue;
    this.catalog = catalog;
    this.borrowers = borrowers;
    this.authService = authService;
    this.time = time;
  }

  /** A member's open holds, READY first, with queue positions. */
  @Transactional(readOnly = true)
  public List<HoldRow> openHolds(UUID memberId) {
    return queue.openHoldsOf(memberId).stream().map(this::row).toList();
  }

  public HoldRow row(Hold hold) {
    return row(queue.view(hold));
  }

  private HoldRow row(HoldView view) {
    Hold hold = view.hold();
    return new HoldRow(
        view,
        catalog.holdableTitle(hold.getBookId()).orElse(null),
        hold.getAssignedCopyId() == null
            ? null
            : catalog.byId(hold.getAssignedCopyId()).orElse(null));
  }

  /** The hold shelf: copies waiting for pickup, soonest deadline first (step 257). */
  @Transactional(readOnly = true)
  public Page<ShelfRow> shelf(int page, int size) {
    PageRequest request = PageRequest.of(page, size);
    Page<Hold> ready = holds.findByStatusOrderByExpiresAtAscReadyAtAsc(HoldStatus.READY, request);
    LocalDate today = time.today();
    Map<UUID, LendableCopy> copies =
        catalog.byIds(ready.getContent().stream().map(Hold::getAssignedCopyId).toList());
    Map<UUID, User> users = new HashMap<>();
    List<ShelfRow> rows =
        ready.getContent().stream()
            .map(
                hold -> {
                  Borrower borrower = borrowers.byId(hold.getMemberId()).orElse(null);
                  User user =
                      borrower == null
                          ? null
                          : users.computeIfAbsent(borrower.userId(), authService::getProfile);
                  return new ShelfRow(
                      hold,
                      copies.get(hold.getAssignedCopyId()),
                      borrower,
                      user == null ? null : user.getFullName(),
                      user == null ? null : user.getPhone(),
                      hold.getExpiresAt().isBefore(today));
                })
            .toList();
    return new PageImpl<>(rows, request, ready.getTotalElements());
  }
}
