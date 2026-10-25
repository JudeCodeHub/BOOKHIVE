package lk.bookhive.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.member.domain.DocumentKind;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.service.ApplicationReviewService;
import lk.bookhive.member.service.ApplicationReviewService.ApplicationView;
import lk.bookhive.member.service.ApplicationReviewService.DocumentView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Staff review queue for membership applications (librarians and admins only). */
@RestController
@RequestMapping("/staff/applications")
@Tag(name = "Staff: membership applications")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffApplicationController {

  private final ApplicationReviewService reviews;

  public StaffApplicationController(ApplicationReviewService reviews) {
    this.reviews = reviews;
  }

  /** One row of the queue. */
  public record ApplicationSummary(
      UUID id,
      String fullName,
      String email,
      String membershipType,
      LocalDate dateOfBirth,
      String guardianMemberNo,
      Instant appliedAt) {

    static ApplicationSummary from(ApplicationView view) {
      Member member = view.member();
      return new ApplicationSummary(
          member.getId(),
          view.applicant().getFullName(),
          view.applicant().getEmail(),
          member.getMembershipType().getCode(),
          member.getDateOfBirth(),
          member.getGuardian() == null ? null : member.getGuardian().getMemberNo(),
          member.getAppliedAt());
    }
  }

  /** Everything staff need to decide, including the NIC (decrypted) and document links. */
  public record ApplicationDetailResponse(
      UUID id,
      MemberStatus status,
      String fullName,
      String email,
      String phone,
      String membershipType,
      String nic,
      LocalDate dateOfBirth,
      String addressLine,
      String city,
      String postalCode,
      String guardianMemberNo,
      Instant appliedAt,
      String memberNo,
      String statusReason,
      List<DocumentLinkResponse> documents) {}

  /**
   * @param url works for a few minutes only; ask for the application again for a fresh one
   */
  public record DocumentLinkResponse(
      UUID id, DocumentKind kind, String contentType, long sizeBytes, URI url) {

    static DocumentLinkResponse from(DocumentView view) {
      return new DocumentLinkResponse(
          view.document().getId(),
          view.document().getKind(),
          view.document().getContentType(),
          view.document().getSizeBytes(),
          view.link());
    }
  }

  public record RejectRequest(@NotBlank @Size(max = 500) String reason) {}

  @GetMapping
  @Operation(summary = "Pending applications, oldest first")
  public PageResponse<ApplicationSummary> pending(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(reviews.pending(page, size), ApplicationSummary::from);
  }

  @GetMapping("/{id}")
  @Operation(summary = "One application with its documents")
  public ApplicationDetailResponse get(@PathVariable UUID id) {
    return detail(reviews.get(id), id);
  }

  @PostMapping("/{id}/approve")
  @Operation(summary = "Approve: assigns the member number, emails the member")
  public ApplicationDetailResponse approve(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    reviews.approve(id, staffId(jwt));
    return detail(reviews.get(id), id);
  }

  @PostMapping("/{id}/reject")
  @Operation(summary = "Reject with a reason, which is emailed to the applicant")
  public ApplicationDetailResponse reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody RejectRequest request) {
    reviews.reject(id, staffId(jwt), request.reason());
    return detail(reviews.get(id), id);
  }

  private ApplicationDetailResponse detail(ApplicationView view, UUID id) {
    Member member = view.member();
    return new ApplicationDetailResponse(
        member.getId(),
        member.getStatus(),
        view.applicant().getFullName(),
        view.applicant().getEmail(),
        view.applicant().getPhone(),
        member.getMembershipType().getCode(),
        member.getNic(),
        member.getDateOfBirth(),
        member.getAddressLine(),
        member.getCity(),
        member.getPostalCode(),
        member.getGuardian() == null ? null : member.getGuardian().getMemberNo(),
        member.getAppliedAt(),
        member.getMemberNo(),
        member.getStatusReason(),
        reviews.documents(id).stream().map(DocumentLinkResponse::from).toList());
  }

  private static UUID staffId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
