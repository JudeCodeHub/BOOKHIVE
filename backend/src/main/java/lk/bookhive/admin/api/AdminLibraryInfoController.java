package lk.bookhive.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.admin.domain.LibraryInfo;
import lk.bookhive.admin.domain.LibraryInfo.About;
import lk.bookhive.admin.domain.LibraryInfo.Contact;
import lk.bookhive.admin.service.LibraryInfoService;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.config.OpenApiConfig;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The library's own details for admins (ADM-6, checklist step 377): name, address, contact, map pin
 * and the "about" text in three languages, all shown on the visit page. Saved with the version it
 * was read at, so two admins can't overwrite each other by accident.
 */
@RestController
@RequestMapping("/admin/library-info")
@Tag(name = "Admin: library info")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminLibraryInfoController {

  /**
   * @param latitude and longitude: the map pin
   * @param aboutEn free text about the library; headings, lists and links are kept
   */
  public record LibraryInfoRequest(
      @NotBlank @Size(max = 150) String name,
      @NotBlank @Size(max = 200) String addressLine,
      @NotBlank @Size(max = 80) String city,
      @Size(max = 10) String postalCode,
      @NotBlank @Size(max = 30) String phone,
      @NotBlank @Email @Size(max = 254) String email,
      @DecimalMin("-90") @DecimalMax("90") double latitude,
      @DecimalMin("-180") @DecimalMax("180") double longitude,
      @Size(max = 20_000) String aboutEn,
      @Size(max = 20_000) String aboutSi,
      @Size(max = 20_000) String aboutTa) {

    Contact contact() {
      return new Contact(
          name.strip(),
          addressLine.strip(),
          city.strip(),
          postalCode == null || postalCode.isBlank() ? null : postalCode.strip(),
          phone.strip(),
          email.strip(),
          latitude,
          longitude);
    }

    About about() {
      return new About(text(aboutEn), text(aboutSi), text(aboutTa));
    }

    private static String text(String value) {
      return value == null ? "" : value;
    }
  }

  /**
   * @param version send it back when saving
   * @param updatedByName null while it still holds the values it was installed with
   */
  public record AdminLibraryInfoResponse(
      String name,
      String addressLine,
      String city,
      @Schema(nullable = true) String postalCode,
      String phone,
      String email,
      double latitude,
      double longitude,
      String aboutEn,
      String aboutSi,
      String aboutTa,
      long version,
      Instant updatedAt,
      @Schema(nullable = true) String updatedByName) {

    static AdminLibraryInfoResponse from(LibraryInfo info, String updatedByName) {
      Contact c = info.getContact();
      About a = info.getAbout();
      return new AdminLibraryInfoResponse(
          c.name(),
          c.addressLine(),
          c.city(),
          c.postalCode(),
          c.phone(),
          c.email(),
          c.latitude(),
          c.longitude(),
          a.en(),
          a.si(),
          a.ta(),
          info.getVersion(),
          info.getUpdatedAt(),
          updatedByName);
    }
  }

  private final LibraryInfoService libraryInfo;
  private final AuthService authService;

  public AdminLibraryInfoController(LibraryInfoService libraryInfo, AuthService authService) {
    this.libraryInfo = libraryInfo;
    this.authService = authService;
  }

  @GetMapping
  @Operation(summary = "The library's details, with who changed them last")
  public AdminLibraryInfoResponse get() {
    return response(libraryInfo.current());
  }

  @PutMapping
  @Operation(summary = "Save them (version: from the last read; 409 if someone else saved since)")
  public AdminLibraryInfoResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam long version,
      @Valid @RequestBody LibraryInfoRequest request) {
    return response(
        libraryInfo.update(
            request.contact(), request.about(), version, UUID.fromString(jwt.getSubject())));
  }

  private AdminLibraryInfoResponse response(LibraryInfo info) {
    UUID adminId = info.getUpdatedBy();
    String name =
        adminId == null
            ? null
            : authService.findUsers(java.util.List.of(adminId)).values().stream()
                .findFirst()
                .map(User::getFullName)
                .orElse(null);
    return AdminLibraryInfoResponse.from(info, name);
  }
}
