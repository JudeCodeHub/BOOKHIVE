package lk.bookhive.fine.event;

import java.util.List;
import java.util.UUID;
import lk.bookhive.common.domain.Money;
import lk.bookhive.fine.domain.PaymentMethod;

/**
 * Money was received for a member's fines (checklist step 318): cash at the desk now (step 276),
 * online payments later. The notification module thanks the member with a receipt.
 *
 * @param reference the cash receipt number or the online order id (unique per payment)
 * @param items what each fine got
 */
public record FinePaymentRecorded(
    UUID memberId, Money amount, PaymentMethod method, String reference, List<Item> items) {

  public record Item(UUID fineId, String description, Money amount) {}
}
