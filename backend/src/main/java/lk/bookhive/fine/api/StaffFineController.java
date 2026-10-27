package lk.bookhive.fine.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.domain.Money;
import lk.bookhive.fine.api.FineResponses.MemberFinesResponse;
import lk.bookhive.fine.service.FinePaymentService;
import lk.bookhive.fine.service.FinePaymentService.CashReceipt;
import lk.bookhive.fine.service.FineQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A member's fines for staff (step 274) and cash payments at the desk (step 276). */
@RestController
@RequestMapping("/staff/members/{memberId}/fines")
@Tag(name = "Staff: fines")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffFineController {

  private final FineQueryService fines;
  private final FinePaymentService payments;

  public StaffFineController(FineQueryService fines, FinePaymentService payments) {
    this.fines = fines;
    this.payments = payments;
  }

  /**
   * @param amount LKR received
   * @param receiptNo the number on the paper receipt given to the member
   * @param fineIds which fines to pay, in order; empty = open fines, oldest first
   */
  public record CashPaymentRequest(
      @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal amount,
      @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9/-]+") String receiptNo,
      List<UUID> fineIds) {}

  public record AllocationSummary(UUID fineId, String description, BigDecimal amount) {}

  /**
   * @param balanceAfter what the member still owes
   */
  public record CashReceiptResponse(
      String receiptNo,
      BigDecimal total,
      List<AllocationSummary> allocations,
      BigDecimal balanceAfter) {

    static CashReceiptResponse from(CashReceipt receipt) {
      return new CashReceiptResponse(
          receipt.receiptNo(),
          receipt.total().amount(),
          receipt.allocations().stream()
              .map(
                  a ->
                      new AllocationSummary(
                          a.fine().getId(), a.fine().getDescription(), a.amount().amount()))
              .toList(),
          receipt.balanceAfter().amount());
    }
  }

  @PostMapping("/cash")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Record cash paid at the desk, with the paper receipt number (oldest first)")
  public CashReceiptResponse recordCash(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID memberId,
      @Valid @RequestBody CashPaymentRequest request) {
    return CashReceiptResponse.from(
        payments.recordCash(
            memberId,
            new Money(request.amount()),
            request.receiptNo(),
            request.fineIds(),
            UUID.fromString(jwt.getSubject())));
  }

  @GetMapping
  @Operation(summary = "A member's balance and fines with their ledgers (open first)")
  public MemberFinesResponse memberFines(
      @PathVariable UUID memberId, @RequestParam(defaultValue = "true") boolean history) {
    return MemberFinesResponse.from(fines.forMember(memberId, history));
  }
}
