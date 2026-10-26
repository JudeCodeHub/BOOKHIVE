package lk.bookhive.circulation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.bookhive.catalog.service.LendingCatalogService.LendableCopy;
import lk.bookhive.circulation.service.CheckoutService;
import lk.bookhive.circulation.service.CheckoutService.CheckoutRefusedException;
import lk.bookhive.circulation.service.CheckoutService.ItemCheck;
import lk.bookhive.circulation.service.CheckoutService.MemberSummary;
import lk.bookhive.circulation.service.CheckoutService.Preview;
import lk.bookhive.circulation.service.CheckoutService.Receipt;
import lk.bookhive.circulation.service.EligibilityChecker.Problem;
import lk.bookhive.circulation.service.EligibilityChecker.Reason;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.member.domain.MemberStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lending at the desk (librarians and admins): preview while scanning, then check out. */
@RestController
@RequestMapping("/staff/checkouts")
@Tag(name = "Staff: circulation")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffCheckoutController {

  private final CheckoutService checkouts;

  public StaffCheckoutController(CheckoutService checkouts) {
    this.checkouts = checkouts;
  }

  /**
   * @param memberNo the scanned library card, e.g. BH-000123
   * @param barcodes the scanned copy labels, e.g. C0000123
   * @param overrideReason admins only: lend despite overridable problems (step 228); ignored by the
   *     preview
   */
  public record CheckoutRequest(
      @NotBlank @Size(max = 20) String memberNo,
      @NotNull @Size(min = 1, max = CheckoutService.MAX_ITEMS)
          List<@Size(max = 30) String> barcodes,
      @Size(min = 10, max = 500) String overrideReason) {}

  /**
   * The preview also works with no items yet: right after the member's card is scanned.
   *
   * @param barcodes the items scanned so far (may be empty)
   */
  public record CheckoutPreviewRequest(
      @NotBlank @Size(max = 20) String memberNo,
      @Size(max = CheckoutService.MAX_ITEMS) List<@Size(max = 30) String> barcodes) {}

  /** One reason; {@code overridable} = staff may lend anyway with a reason (step 228). */
  public record ProblemSummary(Reason reason, boolean overridable, String message) {

    static List<ProblemSummary> from(List<Problem> problems) {
      return problems.stream()
          .map(p -> new ProblemSummary(p.reason(), p.reason().overridable(), p.message()))
          .toList();
    }
  }

  public record MemberCardSummary(
      UUID memberId,
      String memberNo,
      String fullName,
      MemberStatus status,
      LocalDate expiresAt,
      int itemsOut,
      int maxItems,
      int loanDays,
      List<ProblemSummary> problems) {

    static MemberCardSummary from(MemberSummary m) {
      return new MemberCardSummary(
          m.borrower().memberId(),
          m.borrower().memberNo(),
          m.fullName(),
          m.borrower().status(),
          m.borrower().expiresAt(),
          m.itemsOut(),
          m.borrower().maxItems(),
          m.borrower().loanDays(),
          ProblemSummary.from(m.problems()));
    }
  }

  /**
   * @param title null when the barcode is unknown
   */
  public record ItemSummary(
      String barcode, UUID copyId, String title, LocalDate dueDate, List<ProblemSummary> problems) {

    static ItemSummary from(ItemCheck item) {
      LendableCopy copy = item.copy();
      return new ItemSummary(
          item.barcode(),
          copy == null ? null : copy.copyId(),
          copy == null ? null : copy.title(),
          item.dueDate(),
          ProblemSummary.from(item.problems()));
    }
  }

  /**
   * @param canOverride refused, but an admin may lend anyway with a reason
   */
  public record CheckoutPreviewResponse(
      MemberCardSummary member, List<ItemSummary> items, boolean canCheckOut, boolean canOverride) {

    static CheckoutPreviewResponse from(Preview preview) {
      return new CheckoutPreviewResponse(
          MemberCardSummary.from(preview.member()),
          preview.items().stream().map(ItemSummary::from).toList(),
          preview.canCheckOut(),
          preview.canOverride());
    }
  }

  public record LoanReceiptSummary(
      UUID loanId, String barcode, String title, Instant checkedOutAt, LocalDate dueDate) {}

  public record CheckoutReceiptResponse(MemberCardSummary member, List<LoanReceiptSummary> loans) {

    static CheckoutReceiptResponse from(Receipt receipt) {
      return new CheckoutReceiptResponse(
          MemberCardSummary.from(receipt.member()),
          receipt.loans().stream()
              .map(
                  l ->
                      new LoanReceiptSummary(
                          l.loan().getId(),
                          l.copy().barcode(),
                          l.copy().title(),
                          l.loan().getCheckedOutAt(),
                          l.loan().getDueDate()))
              .toList());
    }
  }

  @PostMapping("/preview")
  @Operation(summary = "What would happen: member alerts, per-item problems and due dates")
  public CheckoutPreviewResponse preview(@Valid @RequestBody CheckoutPreviewRequest request) {
    return CheckoutPreviewResponse.from(
        checkouts.preview(
            request.memberNo(), request.barcodes() == null ? List.of() : request.barcodes()));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary =
          "Check out: all items or none (422 CHECKOUT_REFUSED lists the problems; admins may"
              + " override with a reason)")
  public CheckoutReceiptResponse checkOut(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CheckoutRequest request) {
    return CheckoutReceiptResponse.from(
        checkouts.checkOut(
            request.memberNo(),
            request.barcodes(),
            UUID.fromString(jwt.getSubject()),
            blankToNull(request.overrideReason())));
  }

  static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text;
  }

  /** The refusal carries the same per-item report as the preview, in the error body. */
  @ExceptionHandler(CheckoutRefusedException.class)
  public ProblemDetail refused(CheckoutRefusedException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    problem.setProperty("code", ex.getCode());
    problem.setProperty("checkout", CheckoutPreviewResponse.from(ex.getDetails()));
    return problem;
  }
}
