package lk.bookhive.catalog.api;

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
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.domain.AcquisitionSource;
import lk.bookhive.catalog.domain.Copy;
import lk.bookhive.catalog.domain.CopyCondition;
import lk.bookhive.catalog.domain.CopyStatus;
import lk.bookhive.catalog.domain.CopyStatusChange;
import lk.bookhive.catalog.domain.LendingType;
import lk.bookhive.catalog.service.CopyService;
import lk.bookhive.catalog.service.LabelSheetService;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.domain.Money;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Physical copies: add, edit, withdraw, print labels (librarians and admins). */
@RestController
@RequestMapping("/staff")
@Tag(name = "Staff: copies")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffCopyController {

  private final CopyService copies;
  private final LabelSheetService labels;

  public StaffCopyController(CopyService copies, LabelSheetService labels) {
    this.copies = copies;
    this.labels = labels;
  }

  /** The details staff fill in; the same for adding and editing. */
  public record CopyDetailsRequest(
      @NotNull LendingType lendingType,
      @NotNull CopyCondition condition,
      @Size(max = 100) String shelfLocation,
      @PastOrPresent LocalDate acquiredAt,
      @NotNull AcquisitionSource acquisitionSource,
      @DecimalMin("0.00") @DecimalMax("10000000.00") BigDecimal price,
      @Size(max = 500) String notes) {

    Copy.Details details() {
      return new Copy.Details(
          lendingType,
          condition,
          shelfLocation,
          acquiredAt,
          acquisitionSource,
          price == null ? null : new Money(price),
          notes);
    }
  }

  /**
   * @param barcode an existing label's barcode; empty = generate (C0000123)
   * @param count how many identical copies to add (1 when a barcode is given)
   */
  public record AddCopiesRequest(
      @Pattern(regexp = "^$|^[A-Za-z0-9-]{3,30}$", message = "letters, digits and -")
          String barcode,
      @Min(1) @Max(CopyService.MAX_COPIES_AT_ONCE) int count,
      @NotNull @Valid CopyDetailsRequest details) {}

  public record WithdrawRequest(@NotBlank @Size(max = 500) String reason) {}

  public record CopyResponse(
      UUID id,
      UUID bookId,
      String barcode,
      CopyStatus status,
      LendingType lendingType,
      CopyCondition condition,
      String shelfLocation,
      LocalDate acquiredAt,
      AcquisitionSource acquisitionSource,
      BigDecimal price,
      String notes) {

    static CopyResponse from(Copy copy) {
      return new CopyResponse(
          copy.getId(),
          copy.getBook().getId(),
          copy.getBarcode(),
          copy.getStatus(),
          copy.getLendingType(),
          copy.getCondition(),
          copy.getShelfLocation(),
          copy.getAcquiredAt(),
          copy.getAcquisitionSource(),
          copy.getPrice() == null ? null : copy.getPrice().amount(),
          copy.getNotes());
    }
  }

  @GetMapping("/books/{bookId}/copies")
  @Operation(summary = "All copies of a book, withdrawn ones too")
  public List<CopyResponse> list(@PathVariable UUID bookId) {
    return copies.forBook(bookId).stream().map(CopyResponse::from).toList();
  }

  @PostMapping("/books/{bookId}/copies")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add one or more copies (barcodes generated unless one is given)")
  public List<CopyResponse> add(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID bookId,
      @Valid @RequestBody AddCopiesRequest request) {
    return copies
        .add(bookId, request.details().details(), request.barcode(), request.count(), staffId(jwt))
        .stream()
        .map(CopyResponse::from)
        .toList();
  }

  @PatchMapping("/copies/{id}")
  @Operation(summary = "Edit a copy's details (not its status: that changes through actions)")
  public CopyResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody CopyDetailsRequest request) {
    return CopyResponse.from(copies.update(id, request.details(), staffId(jwt)));
  }

  @PostMapping("/copies/{id}/withdraw")
  @Operation(summary = "Withdraw a copy (409 COPY_IN_USE while on loan or on the hold shelf)")
  public CopyResponse withdraw(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody WithdrawRequest request) {
    return CopyResponse.from(copies.withdraw(id, request.reason(), staffId(jwt)));
  }

  /** One line of a copy's history. */
  public record CopyHistoryResponse(
      CopyStatus fromStatus,
      CopyStatus toStatus,
      String reason,
      UUID changedBy,
      Instant changedAt) {

    static CopyHistoryResponse from(CopyStatusChange change) {
      return new CopyHistoryResponse(
          change.getFromStatus(),
          change.getToStatus(),
          change.getReason(),
          change.getChangedBy(),
          change.getChangedAt());
    }
  }

  @GetMapping("/copies/{id}/history")
  @Operation(summary = "Every status change of a copy, newest first")
  public List<CopyHistoryResponse> history(@PathVariable UUID id) {
    return copies.history(id).stream().map(CopyHistoryResponse::from).toList();
  }

  @GetMapping(path = "/copies/labels.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  @Operation(summary = "Barcode label sheet (A4, 3 x 8) for chosen copies or all copies of a book")
  public ResponseEntity<byte[]> labels(
      @RequestParam(required = false) @Size(max = 240) List<UUID> ids,
      @RequestParam(required = false) UUID bookId) {
    byte[] pdf = labels.pdf(copies.forLabels(ids, bookId));
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename("bookhive-labels.pdf").build().toString())
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }

  private static UUID staffId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
