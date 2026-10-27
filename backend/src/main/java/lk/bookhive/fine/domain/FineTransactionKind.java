package lk.bookhive.fine.domain;

/** One line of a fine's ledger. {@link #sign()} says which way it moves the balance. */
public enum FineTransactionKind {
  CHARGE(1),
  PAYMENT(-1),
  WAIVER(-1),
  CANCELLATION(-1),
  REFUND(1);

  private final int sign;

  FineTransactionKind(int sign) {
    this.sign = sign;
  }

  public int sign() {
    return sign;
  }
}
