package lk.bookhive.hold.domain;

/** Where a hold stands (V23). WAITING and READY are open; the rest are closed for good. */
public enum HoldStatus {
  WAITING,
  READY,
  FULFILLED,
  CANCELLED,
  EXPIRED;

  public boolean isOpen() {
    return this == WAITING || this == READY;
  }
}
