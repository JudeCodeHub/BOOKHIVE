package lk.bookhive.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.domain.Language;
import lk.bookhive.member.domain.Member;
import lk.bookhive.member.domain.MemberStatus;
import lk.bookhive.member.domain.MembershipType;
import lk.bookhive.member.domain.RequiredDocument;
import lk.bookhive.member.service.MembershipApplicationService;
import lk.bookhive.member.service.MembershipApplicationService.ApplicationForm;
import lk.bookhive.member.service.MembershipApplicationService.Upload;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Apply for library membership (MEM-1). The applicant must have an account and be logged in; the
 * application is linked to that account.
 */
@RestController
@RequestMapping("/memberships")
@Tag(name = "Membership")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MembershipApplicationController {

  private final MembershipApplicationService applications;

  public MembershipApplicationController(MembershipApplicationService applications) {
    this.applications = applications;
  }

  /**
   * A membership type as shown on the application form, with its name in all three languages.
   *
   * @param minAge null = no lower limit; maxAge null = no upper limit
   * @param annualFee LKR per year, 0 = free
   */
  public record MembershipTypeResponse(
      String code,
      String nameEn,
      String nameSi,
      String nameTa,
      Integer minAge,
      Integer maxAge,
      int maxItems,
      int loanDays,
      int maxRenewals,
      int maxHolds,
      BigDecimal annualFee,
      RequiredDocument requiredDocument,
      boolean requiresGuardian) {

    static MembershipTypeResponse from(MembershipType type) {
      return new MembershipTypeResponse(
          type.getCode(),
          type.getName(Language.EN),
          type.getName(Language.SI),
          type.getName(Language.TA),
          type.getMinAge(),
          type.getMaxAge(),
          type.getMaxItems(),
          type.getLoanDays(),
          type.getMaxRenewals(),
          type.getMaxHolds(),
          type.getAnnualFee().amount(),
          type.getRequiredDocument(),
          type.isRequiresGuardian());
    }
  }

  /** The form fields, sent as the JSON part "application" of a multipart request. */
  public record ApplicationRequest(
      @NotBlank @Size(max = 30) String membershipType,
      @Size(max = 20) String nic,
      @NotNull @Past LocalDate dateOfBirth,
      @NotBlank @Size(max = 200) String addressLine,
      @NotBlank @Size(max = 80) String city,
      @Pattern(regexp = "^$|^[0-9]{5}$", message = "must be 5 digits") String postalCode,
      // Junior applications only: the parent's or guardian's member number, e.g. BH-000123
      @Size(max = 12) String guardianMemberNo) {}

  public record ApplicationResponse(
      UUID id, MemberStatus status, String membershipType, Instant appliedAt) {

    static ApplicationResponse from(Member member) {
      return new ApplicationResponse(
          member.getId(),
          member.getStatus(),
          member.getMembershipType().getCode(),
          member.getAppliedAt());
    }
  }

  @GetMapping("/types")
  @Operation(summary = "Membership types open for applications (public)")
  public List<MembershipTypeResponse> types() {
    return applications.activeTypes().stream().map(MembershipTypeResponse::from).toList();
  }

  @PostMapping(path = "/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Apply for membership (form + NIC / student ID photo)")
  public ApplicationResponse apply(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestPart("application") ApplicationRequest request,
      @RequestPart(name = "document", required = false) MultipartFile document)
      throws IOException {
    ApplicationForm form =
        new ApplicationForm(
            request.membershipType(),
            request.nic(),
            request.dateOfBirth(),
            request.addressLine(),
            request.city(),
            request.postalCode(),
            request.guardianMemberNo());
    Upload upload =
        document == null || document.isEmpty()
            ? null
            : new Upload(
                document.getInputStream(), document.getSize(), document.getOriginalFilename());
    Member member = applications.apply(UUID.fromString(jwt.getSubject()), form, upload);
    return ApplicationResponse.from(member);
  }
}
