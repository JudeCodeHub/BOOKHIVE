package lk.bookhive.circulation.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Staff ticked "email the receipt" at checkout (CIRC-8, checklist step 239). Published after the
 * loans are made; the notification module sends it once the checkout commits.
 */
public record CheckoutReceiptRequested(
    UUID userId, String memberNo, Instant checkedOutAt, List<Line> lines) {

  /** One book on the receipt. */
  public record Line(String title, String barcode, LocalDate dueDate) {}
}
