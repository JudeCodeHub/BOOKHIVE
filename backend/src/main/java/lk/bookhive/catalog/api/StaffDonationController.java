package lk.bookhive.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.domain.Copy;
import lk.bookhive.catalog.domain.CopyStatus;
import lk.bookhive.catalog.domain.Donation;
import lk.bookhive.catalog.service.DonationService;
import lk.bookhive.catalog.service.DonationService.DonationView;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The donations log (librarians and admins). Workflow: record the donation, then add the copies to
 * their books with this donation's id.
 */
@RestController
@RequestMapping("/staff/donations")
@Tag(name = "Staff: donations")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffDonationController {

  private final DonationService donations;

  public StaffDonationController(DonationService donations) {
    this.donations = donations;
  }

  public record DonationRequest(
      @NotBlank @Size(max = 200) String donorName,
      @Size(max = 200) String donorContact,
      @NotNull @PastOrPresent LocalDate receivedAt,
      @Size(max = 1000) String notes) {}

  public record DonationResponse(
      UUID id,
      String donorName,
      String donorContact,
      LocalDate receivedAt,
      String notes,
      long copyCount) {

    static DonationResponse from(DonationView view) {
      Donation d = view.donation();
      return new DonationResponse(
          d.getId(),
          d.getDonorName(),
          d.getDonorContact(),
          d.getReceivedAt(),
          d.getNotes(),
          view.copyCount());
    }
  }

  /** One donated copy, with its book's title. */
  public record DonatedCopySummary(
      UUID copyId, String barcode, CopyStatus status, UUID bookId, String title) {

    static DonatedCopySummary from(Copy copy) {
      return new DonatedCopySummary(
          copy.getId(),
          copy.getBarcode(),
          copy.getStatus(),
          copy.getBook().getId(),
          copy.getBook().getTitle());
    }
  }

  public record DonationDetailResponse(
      DonationResponse donation, List<DonatedCopySummary> copies) {}

  @GetMapping
  @Operation(summary = "Donations, newest first; optionally by part of the donor's name")
  public PageResponse<DonationResponse> list(
      @RequestParam(defaultValue = "") @Size(max = 200) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(donations.list(q, page, size), DonationResponse::from);
  }

  @GetMapping("/{id}")
  @Operation(summary = "One donation with the copies that came from it")
  public DonationDetailResponse get(@PathVariable UUID id) {
    List<Copy> copies = donations.copiesOf(id);
    return new DonationDetailResponse(
        DonationResponse.from(new DonationView(donations.get(id), copies.size())),
        copies.stream().map(DonatedCopySummary::from).toList());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Record a donation")
  public DonationResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DonationRequest request) {
    Donation donation =
        donations.create(
            request.donorName(),
            request.donorContact(),
            request.receivedAt(),
            request.notes(),
            staffId(jwt));
    return DonationResponse.from(new DonationView(donation, 0));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Correct a donation's details")
  public DonationResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody DonationRequest request) {
    Donation donation =
        donations.update(
            id,
            request.donorName(),
            request.donorContact(),
            request.receivedAt(),
            request.notes(),
            staffId(jwt));
    return DonationResponse.from(new DonationView(donation, donations.copiesOf(id).size()));
  }

  private static UUID staffId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
