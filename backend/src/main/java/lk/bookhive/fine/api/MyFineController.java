package lk.bookhive.fine.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.fine.api.FineResponses.MemberFinesResponse;
import lk.bookhive.fine.service.FineQueryService;
import lk.bookhive.member.service.BorrowerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in member's fines and balance (step 274). */
@RestController
@RequestMapping("/me/fines")
@Tag(name = "My fines")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MyFineController {

  private final FineQueryService fines;
  private final BorrowerService borrowers;

  public MyFineController(FineQueryService fines, BorrowerService borrowers) {
    this.fines = fines;
    this.borrowers = borrowers;
  }

  @GetMapping
  @Operation(summary = "My balance and fines (open first); history=true adds settled ones")
  public MemberFinesResponse mine(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "false") boolean history) {
    UUID memberId =
        borrowers
            .byUserId(UUID.fromString(jwt.getSubject()))
            .orElseThrow(
                () ->
                    new NotFoundException("MEMBER_NOT_FOUND", "You are not a library member yet."))
            .memberId();
    return MemberFinesResponse.from(fines.forMember(memberId, history));
  }
}
