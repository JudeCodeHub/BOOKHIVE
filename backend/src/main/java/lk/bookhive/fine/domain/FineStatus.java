package lk.bookhive.fine.domain;

/** OPEN: something to pay (or still growing); SETTLED: down to zero; CANCELLED: withdrawn. */
public enum FineStatus {
  OPEN,
  SETTLED,
  CANCELLED
}
