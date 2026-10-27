package lk.bookhive.payment.domain;

/** Where an online payment stands (V26). Only PENDING may still change to a final status. */
public enum PaymentStatus {
  PENDING,
  SUCCEEDED,
  FAILED,
  CANCELLED,
  CHARGEDBACK;

  /**
   * PayHere's {@code status_code}: 2 success, 0 pending, -1 cancelled, -2 failed, -3 chargeback.
   */
  public static PaymentStatus fromPayHere(int statusCode) {
    return switch (statusCode) {
      case 2 -> SUCCEEDED;
      case 0 -> PENDING;
      case -1 -> CANCELLED;
      case -3 -> CHARGEDBACK;
      default -> FAILED;
    };
  }
}
