package lk.bookhive.hold.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.hold.api.HoldResponses.HoldShelfSummary;
import lk.bookhive.hold.api.HoldResponses.HoldSummary;
import lk.bookhive.hold.service.HoldListService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Holds for staff: the hold shelf, placing and cancelling holds for members (HOLD-5/6). */
@RestController
@RequestMapping("/staff")
@Tag(name = "Staff: holds")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffHoldController {

  private final HoldService holds;
  private final HoldListService lists;
  private final BorrowerService borrowers;

  public StaffHoldController(HoldService holds, HoldListService lists, BorrowerService borrowers) {
    this.holds = holds;
    this.lists = lists;
    this.borrowers = borrowers;
  }

  /**
   * @param memberNo the member's card, e.g. BH-000123
   */
  public record StaffPlaceHoldRequest(
      @NotBlank @Size(max = 20) String memberNo, @NotNull UUID bookId) {}

  public record StaffCancelHoldRequest(@NotBlank @Size(max = 500) String reason) {}

  @GetMapping("/holds/shelf")
  @Operation(summary = "The hold shelf: copies waiting for pickup, soonest deadline first")
  public PageResponse<HoldShelfSummary> shelf(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    return PageResponse.from(lists.shelf(page, size), HoldShelfSummary::from);
  }

  @PostMapping("/holds")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Place a hold for a member at the desk (same rules as online)")
  public HoldSummary place(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StaffPlaceHoldRequest request) {
    UUID memberId =
        borrowers
            .byMemberNo(request.memberNo())
            .orElseThrow(
                () -> new NotFoundException("MEMBER_NOT_FOUND", "No member has this number."))
            .memberId();
    return HoldSummary.from(
        lists.row(holds.place(memberId, request.bookId(), UUID.fromString(jwt.getSubject()))));
  }

  @PostMapping("/holds/{holdId}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Cancel a member's hold, with a reason (audited)")
  public void cancel(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID holdId,
      @Valid @RequestBody StaffCancelHoldRequest request) {
    holds.cancel(holdId, UUID.fromString(jwt.getSubject()), null, request.reason().strip());
  }

  @GetMapping("/members/{memberId}/holds")
  @Operation(summary = "A member's open holds (ready first, then queue positions)")
  public List<HoldSummary> memberHolds(@PathVariable UUID memberId) {
    return lists.openHolds(memberId).stream().map(HoldSummary::from).toList();
  }

  @ExceptionHandler(HoldRefusedException.class)
  public ProblemDetail refused(HoldRefusedException ex) {
    return HoldResponses.refused(ex);
  }
}
