package lk.bookhive.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.member.api.MembershipController.RenewalResponse;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.service.MembershipService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff actions on an existing member (librarians and admins). Search comes in step 144. */
@RestController
@RequestMapping("/staff/members")
@Tag(name = "Staff: members")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffMemberController {

  private final MembershipService memberships;

  public StaffMemberController(MembershipService memberships) {
    this.memberships = memberships;
  }

  public record SuspendRequest(@NotBlank @Size(max = 500) String reason) {}

  public record ReactivateRequest(@Size(max = 500) String note) {}

  public record MemberStatusResponse(
      UUID id, String memberNo, MemberStatus status, LocalDate expiresAt, String statusReason) {

    static MemberStatusResponse from(Member member) {
      return new MemberStatusResponse(
          member.getId(),
          member.getMemberNo(),
          member.getStatus(),
          member.getExpiresAt(),
          member.getStatusReason());
    }
  }

  @PostMapping("/{id}/renew")
  @Operation(summary = "Renew at the desk")
  public RenewalResponse renew(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return RenewalResponse.from(memberships.renewByStaff(id, staffId(jwt)));
  }

  @PostMapping("/{id}/suspend")
  @Operation(summary = "Suspend with a reason (blocks borrowing)")
  public MemberStatusResponse suspend(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody SuspendRequest request) {
    return MemberStatusResponse.from(memberships.suspend(id, staffId(jwt), request.reason()));
  }

  @PostMapping("/{id}/reactivate")
  @Operation(summary = "Lift a suspension (becomes EXPIRED if the year is over)")
  public MemberStatusResponse reactivate(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) ReactivateRequest request) {
    String note = request == null ? null : request.note();
    return MemberStatusResponse.from(memberships.reactivate(id, staffId(jwt), note));
  }

  private static UUID staffId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
