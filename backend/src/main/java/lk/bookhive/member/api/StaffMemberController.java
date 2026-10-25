package lk.bookhive.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.member.api.MembershipController.RenewalResponse;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.service.MemberLookupService;
import lk.bookhive.member.service.MemberLookupService.MemberProfile;
import lk.bookhive.member.service.MemberLookupService.MemberView;
import lk.bookhive.member.service.MembershipService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Finding members and acting on them at the desk (librarians and admins). */
@RestController
@RequestMapping("/staff/members")
@Tag(name = "Staff: members")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffMemberController {

  private final MembershipService memberships;
  private final MemberLookupService lookup;

  public StaffMemberController(MembershipService memberships, MemberLookupService lookup) {
    this.memberships = memberships;
    this.lookup = lookup;
  }

  /** One search result. */
  public record MemberSummary(
      UUID id,
      String memberNo,
      String fullName,
      String email,
      String phone,
      String membershipType,
      MemberStatus status,
      LocalDate expiresAt) {

    static MemberSummary from(MemberView view) {
      Member member = view.member();
      return new MemberSummary(
          member.getId(),
          member.getMemberNo(),
          view.user().getFullName(),
          view.user().getEmail(),
          view.user().getPhone(),
          member.getMembershipType().getCode(),
          member.getStatus(),
          member.getExpiresAt());
    }
  }

  /** Everything the desk needs about one member. Loans, holds and fines join with circulation. */
  public record MemberProfileResponse(
      UUID id,
      String memberNo,
      MemberStatus status,
      String statusReason,
      String fullName,
      String email,
      String phone,
      String preferredLanguage,
      String membershipType,
      int maxItems,
      int loanDays,
      LocalDate dateOfBirth,
      String nic,
      String addressLine,
      String city,
      String postalCode,
      Instant approvedAt,
      LocalDate expiresAt,
      MemberSummary guardian,
      List<MemberSummary> juniors) {}

  @GetMapping
  @Operation(summary = "Search by member number, NIC, phone, email or name")
  public PageResponse<MemberSummary> search(
      @RequestParam(defaultValue = "") @Size(max = 100) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    if (q.isBlank()) {
      return PageResponse.from(Page.<MemberSummary>empty(PageRequest.of(page, size)));
    }
    return PageResponse.from(lookup.search(q, page, size), MemberSummary::from);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Member profile for the desk")
  public MemberProfileResponse profile(@PathVariable UUID id) {
    MemberProfile profile = lookup.profile(id);
    Member member = profile.member();
    Member guardian = member.getGuardian();
    return new MemberProfileResponse(
        member.getId(),
        member.getMemberNo(),
        member.getStatus(),
        member.getStatusReason(),
        profile.user().getFullName(),
        profile.user().getEmail(),
        profile.user().getPhone(),
        profile.user().getPreferredLanguage().code(),
        member.getMembershipType().getCode(),
        member.getMembershipType().getMaxItems(),
        member.getMembershipType().getLoanDays(),
        member.getDateOfBirth(),
        member.getNic(),
        member.getAddressLine(),
        member.getCity(),
        member.getPostalCode(),
        member.getApprovedAt(),
        member.getExpiresAt(),
        guardian == null ? null : MemberSummary.from(lookup.profileView(guardian)),
        profile.juniors().stream().map(MemberSummary::from).toList());
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
