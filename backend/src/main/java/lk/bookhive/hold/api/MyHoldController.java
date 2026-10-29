package lk.bookhive.hold.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.hold.api.HoldResponses.HoldCheckResponse;
import lk.bookhive.hold.api.HoldResponses.HoldProblemSummary;
import lk.bookhive.hold.api.HoldResponses.HoldSummary;
import lk.bookhive.hold.service.HoldListService;
import lk.bookhive.hold.service.HoldQueueService;
import lk.bookhive.hold.service.HoldService;
import lk.bookhive.hold.service.HoldService.HoldRefusedException;
import lk.bookhive.member.service.BorrowerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in member's holds: check, place, list, cancel (HOLD-1/2/5). */
@RestController
@RequestMapping("/me/holds")
@Tag(name = "My holds")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MyHoldController {

  private final HoldService holds;
  private final HoldListService lists;
  private final HoldQueueService queue;
  private final BorrowerService borrowers;

  public MyHoldController(
      HoldService holds, HoldListService lists, HoldQueueService queue, BorrowerService borrowers) {
    this.holds = holds;
    this.lists = lists;
    this.queue = queue;
    this.borrowers = borrowers;
  }

  public record PlaceHoldRequest(@NotNull UUID bookId) {}

  @GetMapping
  @Operation(summary = "My open holds: ready to pick up first, then my place in each queue")
  public List<HoldSummary> mine(@AuthenticationPrincipal Jwt jwt) {
    return lists.openHolds(memberId(jwt)).stream().map(HoldSummary::from).toList();
  }

  @GetMapping("/check/{bookId}")
  @Operation(summary = "Can I reserve this book now, and if not, why (with the queue length)")
  public HoldCheckResponse check(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookId) {
    var problems = holds.check(memberId(jwt), bookId);
    return new HoldCheckResponse(
        problems.isEmpty(), queue.queueLength(bookId), HoldProblemSummary.from(problems));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Reserve a book (422 HOLD_REFUSED lists every reason)")
  public HoldSummary place(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlaceHoldRequest request) {
    return HoldSummary.from(lists.row(holds.place(memberId(jwt), request.bookId(), null)));
  }

  @PostMapping("/{holdId}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Cancel my hold (a copy set aside goes to the next in line)")
  public void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID holdId) {
    holds.cancel(holdId, UUID.fromString(jwt.getSubject()), memberId(jwt), null);
  }

  @ExceptionHandler(HoldRefusedException.class)
  public ProblemDetail refused(HoldRefusedException ex) {
    return HoldResponses.refused(ex);
  }

  private UUID memberId(Jwt jwt) {
    return borrowers.memberIdOfUser(UUID.fromString(jwt.getSubject()));
  }
}
