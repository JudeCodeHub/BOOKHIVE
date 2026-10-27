package lk.bookhive.fine.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.fine.api.FineResponses.MemberFinesResponse;
import lk.bookhive.fine.service.FineQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A member's fines for staff (step 274); payments and waivers come in steps 276-277. */
@RestController
@RequestMapping("/staff/members/{memberId}/fines")
@Tag(name = "Staff: fines")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffFineController {

  private final FineQueryService fines;

  public StaffFineController(FineQueryService fines) {
    this.fines = fines;
  }

  @GetMapping
  @Operation(summary = "A member's balance and fines with their ledgers (open first)")
  public MemberFinesResponse memberFines(
      @PathVariable UUID memberId, @RequestParam(defaultValue = "true") boolean history) {
    return MemberFinesResponse.from(fines.forMember(memberId, history));
  }
}
