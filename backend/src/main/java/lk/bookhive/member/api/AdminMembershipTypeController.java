package lk.bookhive.member.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.domain.Money;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.member.domain.MembershipType;
import lk.bookhive.member.domain.MembershipType.Rules;
import lk.bookhive.member.domain.RequiredDocument;
import lk.bookhive.member.service.MembershipTypeAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Membership types and their borrowing rules (admins only, via /admin/**). */
@RestController
@RequestMapping("/admin/membership-types")
@Tag(name = "Admin: membership types")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminMembershipTypeController {

  private final MembershipTypeAdminService service;

  public AdminMembershipTypeController(MembershipTypeAdminService service) {
    this.service = service;
  }

  /** Create (with a code) or edit (code ignored: it can't change). */
  public record MembershipTypeRequest(
      @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{1,29}$", message = "letters, digits and _")
          String code,
      @NotBlank @Size(max = 60) String nameEn,
      @NotBlank @Size(max = 60) String nameSi,
      @NotBlank @Size(max = 60) String nameTa,
      @Min(0) @Max(120) Integer minAge,
      @Min(0) @Max(120) Integer maxAge,
      @Min(0) @Max(50) int maxItems,
      @Min(1) @Max(120) int loanDays,
      @Min(0) @Max(20) int maxRenewals,
      @Min(0) @Max(20) int maxHolds,
      @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") BigDecimal annualFee,
      @NotNull RequiredDocument requiredDocument,
      boolean requiresGuardian,
      List<@NotBlank @Size(max = 30) String> allowedSections,
      boolean active) {

    Rules rules() {
      return new Rules(
          nameEn.strip(),
          nameSi.strip(),
          nameTa.strip(),
          minAge,
          maxAge,
          maxItems,
          loanDays,
          maxRenewals,
          maxHolds,
          new Money(annualFee),
          requiredDocument,
          requiresGuardian,
          allowedSections,
          active);
    }
  }

  public record AdminMembershipTypeResponse(
      UUID id,
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
      boolean requiresGuardian,
      List<String> allowedSections,
      boolean active) {

    static AdminMembershipTypeResponse from(MembershipType type) {
      return new AdminMembershipTypeResponse(
          type.getId(),
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
          type.isRequiresGuardian(),
          type.getAllowedSections() == null ? List.of() : type.getAllowedSections(),
          type.isActive());
    }
  }

  @GetMapping
  @Operation(summary = "All membership types, retired ones too")
  public List<AdminMembershipTypeResponse> all() {
    return service.all().stream().map(AdminMembershipTypeResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a membership type")
  public AdminMembershipTypeResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MembershipTypeRequest request) {
    if (request.code() == null) {
      throw new FieldValidationException("code", "REQUIRED", "Enter a code, e.g. ADULT.");
    }
    return AdminMembershipTypeResponse.from(
        service.create(request.code(), request.rules(), adminId(jwt)));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Change a type's rules or names; set active=false to retire it")
  public AdminMembershipTypeResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody MembershipTypeRequest request) {
    return AdminMembershipTypeResponse.from(service.update(id, request.rules(), adminId(jwt)));
  }

  private static UUID adminId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
