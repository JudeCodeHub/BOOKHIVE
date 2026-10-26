package lk.bookhive.circulation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lk.bookhive.circulation.service.ReturnService;
import lk.bookhive.circulation.service.ReturnService.Destination;
import lk.bookhive.circulation.service.ReturnService.ReturnResult;
import lk.bookhive.common.config.OpenApiConfig;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Returns at the desk (librarians and admins): one scan per item. */
@RestController
@RequestMapping("/staff/returns")
@Tag(name = "Staff: circulation")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffReturnController {

  private final ReturnService returns;

  public StaffReturnController(ReturnService returns) {
    this.returns = returns;
  }

  public record ReturnRequest(@NotBlank @Size(max = 30) String barcode) {}

  /**
   * @param fine late-return fine in LKR (0 if on time or within the grace days); recorded by the
   *     fine module
   * @param shelfLocation where to put it back (SHELF)
   * @param heldForMemberNo whose hold it's set aside for (HOLD_SHELF)
   * @param wasLost the copy had been marked lost: its lost charges are cancelled or refunded
   */
  public record ReturnResponse(
      UUID loanId,
      String barcode,
      String title,
      String memberNo,
      LocalDate dueDate,
      Instant returnedAt,
      long daysLate,
      BigDecimal fine,
      Destination destination,
      String shelfLocation,
      String callNumber,
      String heldForMemberNo,
      boolean wasLost) {

    static ReturnResponse from(ReturnResult r) {
      return new ReturnResponse(
          r.loan().getId(),
          r.copy().barcode(),
          r.copy().title(),
          r.borrower() == null ? null : r.borrower().memberNo(),
          r.loan().getDueDate(),
          r.loan().getReturnedAt(),
          r.fine().daysLate(),
          r.fine().amount().amount(),
          r.routing().destination(),
          r.routing().shelfLocation(),
          r.copy().callNumber(),
          r.routing().heldFor() == null ? null : r.routing().heldFor().memberNo(),
          r.wasLost());
    }
  }

  @PostMapping
  @Operation(summary = "Return a copy by barcode: closes the loan, fine, where it goes next")
  public ReturnResponse returnCopy(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReturnRequest request) {
    return ReturnResponse.from(
        returns.returnCopy(request.barcode(), UUID.fromString(jwt.getSubject())));
  }
}
